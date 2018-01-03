Watcher Rules and Triggers
--------------------------


## Tables

```sql
drop table watcher_origin_suppress;
CREATE TABLE watcher_origin_suppress (
  prefix_bin varbinary(16) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL,
  allowed_origin_as int unsigned NOT NULL,
  notes varchar(255),
  timestamp datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (prefix_bin,prefix_len,allowed_origin_as)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


drop table watcher_prefix_log;
CREATE TABLE watcher_prefix_log (
  prefix_bin varbinary(16) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL,
  peer_hash_id char(32) NOT NULL,

  watcher_rule_num int NOT NULL,
  watcher_rule_name varchar(255) NOT NULL DEFAULT '',
  count int unsigned not null default 1,

  origin_as int unsigned NOT NULL,
  prev_origin_as int unsigned,
  aggregate_prefix_bin varbinary(16),
  aggregate_prefix_len tinyint(3) unsigned,
  aggregate_origin_as int unsigned, 
  
  analysis_output text,
  
  period_ts datetime(6) NOT NULL,
  first_ts datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_ts datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

  PRIMARY KEY (prefix_bin,prefix_len,peer_hash_id,period_ts,watcher_rule_num),
  KEY idx_ts (period_ts),
  KEY idx_origin_as (origin_as),
  KEY idx_prefix_full (prefix_bin,prefix_len),
  KEY idx_prefix (prefix_bin)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED
  PARTITION BY RANGE  COLUMNS(period_ts)
(PARTITION p2017_09 VALUES LESS THAN ('2017-10-01') ENGINE = InnoDB,
 PARTITION p2017_10 VALUES LESS THAN ('2017-11-01') ENGINE = InnoDB,
 PARTITION p2017_11 VALUES LESS THAN ('2017-12-01') ENGINE = InnoDB,
 PARTITION p2017_12 VALUES LESS THAN ('2018-01-01') ENGINE = InnoDB,
 PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);
```

## Triggers

```sql

# the below is disabled because on initial RIB dump it'll record everything as new
#drop trigger as_path_analysis_aft_insert;
#delimiter //
#CREATE TRIGGER as_path_analysis_aft_insert AFTER INSERT on as_path_analysis
#   FOR EACH ROW
#   BEGIN
#       declare period_timestamp timestamp;
#       declare period_seconds int unsigned default 1800;
#       declare local_count int unsigned;
#
#
#      IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN
#
#       # ------------------------------------
#       # Run WATCHER rules/checks
#       # ------------------------------------
#       SET local_count = 0;
#
#       select 1 INTO local_count FROM as_path_analysis where asn = new.asn and asn_left = new.asn_left limit 1;
#
#       IF (local_count = 1 AND new.asn_left > 0) THEN
#        SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);
#
#			# origin is the current ASN and prev_origin is the new transit ASN
#           INSERT IGNORE INTO watcher_prefix_log  (prefix_bin,prefix_len,peer_hash_id,
#                    #watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,last_ts)
#                VALUES (new.prefix_bin,new.prefix_len,'00000000000000000000000000000000',
#                     6, 'NEW_TRANSIT',period_timestamp,new.asn,new.asn_left,new.timestamp)
# 			    	ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
#        END IF;
#     END IF;
#   END;//
#delimiter ;



drop trigger rib_aft_insert;
delimiter //
CREATE TRIGGER rib_aft_insert AFTER INSERT on rib
   FOR EACH ROW
   BEGIN
       declare period_timestamp timestamp;
       declare period_seconds int unsigned default 1800;
       declare local_len int;
       declare local_agg_prefix_bin varbinary(16);
       declare local_agg_prefix_len tinyint(3) unsigned;
       declare local_agg_origin_as int unsigned;
       declare local_count int unsigned;
       declare local_count2 int unsigned;

       # Allow per session disabling of trigger (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
       IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

         # ------------------------------------
         # Run WATCHER rules/checks
         # ------------------------------------

         # Exclude specific entries
         IF (new.origin_as != 23456 AND new.origin_as != 0) THEN

			# Update gen_prefix_validation
		        INSERT IGNORE INTO gen_prefix_validation (prefix,prefix_len,recv_origin_as,rpki_origin_as,irr_origin_as,irr_source,prefix_bits,isIPv4)

                 		             SELECT SQL_SMALL_RESULT new.prefix_bin,new.prefix_len,new.origin_as,
                                 		                          rpki.origin_as, w.origin_as,w.source,new.prefix_bits,new.isIPv4
                                      		 FROM (SELECT new.prefix_bin as prefix_bin, new.prefix_len as prefix_len, new.origin_as as origin_as, new.prefix_bits,
                                                                new.isIPv4) rib
                                                 		LEFT JOIN gen_whois_route w ON (new.prefix_bin = w.prefix AND
                                                          		  new.prefix_len = w.prefix_len)
	                                                 LEFT JOIN rpki_validator rpki ON (new.prefix_bin = rpki.prefix AND
        	                                                    new.prefix_len >= rpki.prefix_len and new.prefix_len <= rpki.prefix_len_max);


         # RULE 4: AGGREGATE_ORIGIN_MISMATCH_NEW_PREFIX - The aggregate origin does not match
         SET local_agg_prefix_bin = null;

         # first find the aggregate
         IF (new.prefix_len > 0) THEN
             set local_len = new.prefix_len - 1;
         ELSE
             set local_len = 0;
         END IF;
 
         bit_loop: LOOP
             #SELECT prefix_bin,prefix_len,origin_as INTO local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as
             #      FROM rib WHERE prefix_bits = LEFT(new.prefix_bits, local_len) 
             #            AND isIPv4 = new.isIPv4 and origin_as != 23456
             #            AND isPrePolicy = new.isPrePolicy and isAdjRibIn = new.isAdjRibIn
             #      LIMIT 1;
             SELECT prefix,prefix_len,recv_origin_as INTO local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as
                   FROM gen_prefix_validation
						   WHERE prefix_bits = LEFT(new.prefix_bits, local_len) AND recv_origin_as != 23456 
                               AND isIPv4 = new.isIPv4
                   LIMIT 1;

             IF (local_agg_prefix_bin is not null OR local_len <= 9) THEN
                LEAVE bit_loop;
             END IF;

             SET local_len = local_len - 1;
 
         END LOOP bit_loop;

### CONSIDER ADDING NEW_MORE_SPECIFIC if local AGG is NULL in order to catch new specifics in general

         IF (local_agg_prefix_bin is not null AND local_agg_origin_as != new.origin_as) THEN
            SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);
            
            SET local_count = 0;

  		    # Insert only if not already inserted in the last 6 hours
            #    We excluded the current interval so that we can get a peer count
            SELECT count(*) INTO local_count FROM watcher_prefix_log
                  WHERE period_ts >= date_sub(period_timestamp, interval 5 hour) 
                        AND period_ts < period_timestamp
                        AND prefix_bin = new.prefix_bin AND prefix_len = new.prefix_len
                        AND aggregate_prefix_bin = local_agg_prefix_bin
                        AND aggregate_prefix_len = local_agg_prefix_len
                        AND origin_as = new.origin_as 
                        AND aggregate_origin_as = local_agg_origin_as;

            IF (local_count = 0) THEN
                   # ------------------------------------
                   # Suppress alerts if prefix origin is in suppress/baseline 
                   #    allowed origin in this case is the aggregate origin that we match against the
                   #    offending prefix (not the aggregate).   Basically the aggregate ASN becomes allowed
                   #    for the more specific. 
                   # ------------------------------------
					SELECT count(*) INTO local_count FROM watcher_origin_suppress
                                          WHERE prefix_bin = new.prefix_bin and prefix_len = new.prefix_len 
                                                     AND allowed_origin_as = local_agg_origin_as limit 1;

                 # Check if prefix is globally withdrawn
                 SET local_count2 = 0;
                    SELECT count(*) INTO local_count2 
                             FROM rib where prefix_bin = new.prefix_bin and prefix_len = new.prefix_len 
                                    AND origin_as = new.origin_as and isWithdrawn = False 
									 AND first_added_timestamp <= date_sub(current_timestamp, interval 30 minute);

               IF (local_count2 = 0 AND local_count = 0) THEN
                INSERT IGNORE INTO watcher_prefix_log  (prefix_bin,prefix_len,peer_hash_id,
                    watcher_rule_num,watcher_rule_name,period_ts,origin_as,
                    aggregate_prefix_bin,aggregate_prefix_len,aggregate_origin_as,last_ts)
		             VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
                     3, 'NEW_MORE_SPECIFIC', period_timestamp,new.origin_as,
                     local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as,new.timestamp)
 			    	ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
            END IF;
         END IF;
         END IF;
       END IF;
       END IF;
   END;//
delimiter ;


drop trigger rib_aft_update;
delimiter //
CREATE TRIGGER rib_aft_update AFTER UPDATE on rib
   FOR EACH ROW
   BEGIN
       declare period_timestamp timestamp;
       declare period_seconds int unsigned default 1800;
       declare local_len int;
       declare local_agg_prefix_bin varbinary(16);
       declare local_agg_prefix_len tinyint(3) unsigned;
       declare local_agg_origin_as int unsigned;
       declare local_count int unsigned;
       declare local_count2 int unsigned;

       # Allow per session disabling of trigger (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
       IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN
         # ------------------------------------
         # Run WATCHER rules/checks
         # ------------------------------------
 
	
		IF (new.origin_as != 23456 AND old.origin_as != 23456
            AND ((new.isWithdrawn = False AND new.origin_as != 0 AND old.origin_as != 0) OR new.isWithdrawn = True)) THEN
            # RULE 1: ORIGIN_MISMATCH - The new prefix origin doesn't match the prefix entry
            IF (old.origin_as != new.origin_as) THEN

               SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);

            SET local_count = 0;

  		    # Insert only if not already inserted in the last 6 hours
            #    We excluded the current interval so that we can get a peer count
            SELECT count(*) INTO local_count FROM watcher_prefix_log
                  WHERE period_ts >= date_sub(period_timestamp, interval 5 hour) 
                        AND period_ts < period_timestamp
                        AND prefix_bin = new.prefix_bin AND prefix_len = new.prefix_len
                        AND origin_as = new.origin_as AND prev_origin_as = old.origin_as;

            IF (local_count = 0) THEN

                   # ------------------------------------
                   # Suppress alerts if prefix origin is in suppress/baseline 
                   # ------------------------------------
                SELECT count(*) INTO local_count FROM watcher_origin_suppress
                                         WHERE prefix_bin = new.prefix_bin and prefix_len = new.prefix_len 
                                                     AND allowed_origin_as = new.origin_as limit 1;

               IF (local_count = 0) THEN
                   INSERT IGNORE INTO watcher_prefix_log (prefix_bin,prefix_len,peer_hash_id,
                          watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,last_ts)
                       VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
                             1, 'ORIGIN_MISMATCH', period_timestamp, new.origin_as, old.origin_as, new.timestamp)
                       ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
               END IF;
            END IF;

#            ELSEIF (new.isWithdrawn = False AND old.isWithdrawn = True) THEN
#			    SET local_count = 0;
#               # RULE 5: PREFIX_LOSS_CLEAR - The prefix is back from being globally removed
#               SELECT watcher_rule_num INTO local_count 
#                     FROM watcher_prefix_log
#                     WHERE prefix_bin = new.prefix_bin 
#                           AND prefix_len = new.prefix_len AND watcher_rule_num in (4,5)
#                     ORDER BY last_ts desc,watcher_rule_num
#                     LIMIT 1;
#
#               IF (local_count = 4) THEN
#                  SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);#
#
#                  INSERT IGNORE INTO watcher_prefix_log (prefix_bin,prefix_len,peer_hash_id,
#                         watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,last_ts)
#					   VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
#                                5, 'PREFIX_LOSS_CLEAR', period_timestamp, new.origin_as, old.origin_as, new.timestamp)
#				   ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
#               END IF;

            ELSEIF (new.isWithdrawn = True) THEN
			   SET local_count = 1;

               # RULE 4: PREFIX_LOSS - The prefix is globally lost, no active entry
               SELECT count(*) INTO local_count 
                     FROM rib where prefix_bin = new.prefix_bin and prefix_len = new.prefix_len and isWithdrawn = False;

               IF (local_count <= 0) THEN
                  SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);

                  INSERT IGNORE INTO watcher_prefix_log (prefix_bin,prefix_len,peer_hash_id,
                         watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,last_ts)
					   VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
                                4, 'PREFIX_LOSS', period_timestamp, old.origin_as, old.origin_as,new.timestamp)
				   ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;

					# Remove prefix from validation table
					DELETE IGNORE FROM gen_prefix_validation where prefix_bits = new.prefix_bits;
               END IF;

            ELSEIF (old.first_added_timestamp >= date_sub(current_timestamp, interval 2 hour)) THEN

               # RULE 2: AGGREGATE_ORIGIN_MISMATCH - The aggregate origin does not match

               SET local_agg_prefix_bin = null;

               # first find the aggregate
            IF (new.prefix_len > 0) THEN
                set local_len = new.prefix_len - 1;
            ELSE
                set local_len = 0;
               END IF; 

               bit_loop: LOOP

				#SELECT prefix_bin,prefix_len,origin_as INTO local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as
                #         FROM rib WHERE prefix_bits = LEFT(new.prefix_bits, local_len) 
                #                 AND isIPv4 = new.isIPv4 and origin_as != 23456
                #                 AND isPrePolicy = new.isPrePolicy and isAdjRibIn = new.isAdjRibIn
                #         LIMIT 1;
             SELECT prefix,prefix_len,recv_origin_as INTO local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as
                   FROM gen_prefix_validation
						   WHERE prefix_bits = LEFT(new.prefix_bits, local_len) AND recv_origin_as != 23456
                            AND isIPv4 = new.isIPv4
                   LIMIT 1;

                  IF (local_agg_prefix_bin is not null OR local_len <= 9) THEN
                     LEAVE bit_loop;
                  END IF;

                  SET local_len = local_len - 1;
 
               END LOOP bit_loop;


               IF (local_agg_prefix_bin is not null AND local_agg_origin_as != new.origin_as) THEN
                     SET period_timestamp = from_unixtime(floor(unix_timestamp(new.timestamp) / period_seconds) * period_seconds);

            SET local_count = 0;

            # Insert only if not already inserted in the last 6 hours
            #    We excluded the current interval so that we can get a peer count
            SELECT count(*) INTO local_count FROM watcher_prefix_log
                  WHERE period_ts >= date_sub(period_timestamp, interval 5 hour) 
                        AND period_ts < period_timestamp
                        AND prefix_bin = new.prefix_bin AND prefix_len = new.prefix_len
                        AND aggregate_prefix_bin = local_agg_prefix_bin
                        AND aggregate_prefix_len = local_agg_prefix_len
                        AND origin_as = new.origin_as AND aggregate_origin_as = local_agg_origin_as;

            IF (local_count = 0) THEN
                          # ------------------------------------
                          # Suppress alerts if prefix origin is in suppress/baseline 
                          #    allowed origin in this case is the aggregate origin that we match against the
                          #    offending prefix (not the aggregate).   Basically the aggregate ASN becomes allowed
                          #    for the more specific. 
                          # ------------------------------------
				       	SELECT count(*) INTO local_count FROM watcher_origin_suppress
                                                 WHERE prefix_bin = new.prefix_bin and prefix_len = new.prefix_len 
                                                            AND allowed_origin_as = local_agg_origin_as limit 1;

                    IF (local_count = 0) THEN

                        # Check if prefix is globally withdrawn
                        SET local_count2 = 0;
                           SELECT count(*) INTO local_count2 
                                       FROM rib where prefix_bin = new.prefix_bin and prefix_len = new.prefix_len 
                                           AND origin_as = new.origin_as and isWithdrawn = False
	   									    AND first_added_timestamp <= date_sub(current_timestamp, interval 30 minute);


							IF (local_count2 = 0) THEN

                           INSERT IGNORE INTO watcher_prefix_log  (prefix_bin,prefix_len,peer_hash_id,
                                   watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,
                                   aggregate_prefix_bin,aggregate_prefix_len,aggregate_origin_as,last_ts)
                               VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
                                   3, 'NEW_MORE_SPECIFIC', period_timestamp, new.origin_as, old.origin_as,
                                   local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as, new.timestamp)
 				              ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
							ELSE
                           INSERT IGNORE INTO watcher_prefix_log  (prefix_bin,prefix_len,peer_hash_id,
                                   watcher_rule_num,watcher_rule_name,period_ts,origin_as,prev_origin_as,
                                   aggregate_prefix_bin,aggregate_prefix_len,aggregate_origin_as,last_ts)
                               VALUES (new.prefix_bin,new.prefix_len,new.peer_hash_id,
                                   2, 'AGGREGATE_MISMATCH', period_timestamp, new.origin_as, old.origin_as,
                                   local_agg_prefix_bin,local_agg_prefix_len,local_agg_origin_as,new.timestamp)
 				              ON DUPLICATE KEY UPDATE last_ts = new.timestamp, count = count + 1;
							END IF;
                    END IF;
                END IF;
               END IF;

            END IF;
         END IF;
      END IF;
   END;//
delimiter ;

```

