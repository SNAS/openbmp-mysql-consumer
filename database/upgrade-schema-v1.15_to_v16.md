DB Alter Changes for v1.17
--------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.16.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE

# Add additional partitions to path_attr_log and withdrawn_log tables
alter table path_attr_log  REORGANIZE PARTITION pOther into (
partition p2017_08 values less than ('2017-09-01') ENGINE = InnoDB,
partition p2017_09 values less than ('2017-10-01') ENGINE = InnoDB,
partition p2017_10 values less than ('2017-11-01') ENGINE = InnoDB, 
partition p2017_11 values less than ('2017-12-01') ENGINE = InnoDB, 
partition p2017_12 values less than ('2018-01-01') ENGINE = InnoDB, 
partition p2018_01 values less than ('2018-02-01') ENGINE = InnoDB, 
partition p2018_02 values less than ('2018-03-01') ENGINE = InnoDB, 
partition p2018_03 values less than ('2018-04-01') ENGINE = InnoDB,
partition p2018_04 values less than ('2018-05-01') ENGINE = InnoDB, 
PARTITION pOther VALUES LESS THAN (MAXVALUE));

alter table withdrawn_log  REORGANIZE PARTITION pOther into (
partition p2017_08 values less than ('2017-09-01') ENGINE = InnoDB,
partition p2017_09 values less than ('2017-10-01') ENGINE = InnoDB,
partition p2017_10 values less than ('2017-11-01') ENGINE = InnoDB, 
partition p2017_11 values less than ('2017-12-01') ENGINE = InnoDB, 
partition p2017_12 values less than ('2018-01-01') ENGINE = InnoDB, 
partition p2018_01 values less than ('2018-02-01') ENGINE = InnoDB, 
partition p2018_02 values less than ('2018-03-01') ENGINE = InnoDB, 
partition p2018_03 values less than ('2018-04-01') ENGINE = InnoDB,
partition p2018_04 values less than ('2018-05-01') ENGINE = InnoDB, 
PARTITION pOther VALUES LESS THAN (MAXVALUE));

# Change the timestamp column to support microseconds in as_path_analysis
alter table as_path_analysis change column
      timestamp `timestamp` datetime(6) NOT NULL
          DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

# Create l3vpn_log table
drop table if exists l3vpn_log;
CREATE TABLE l3vpn_log (
  peer_hash_id char(32) NOT NULL,
  type enum('withdrawn', 'changed') not null,
  prefix varchar(40) NOT NULL,
  rd varchar(30) NOT NULL,
  prefix_len int(10) unsigned NOT NULL,
  timestamp datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  path_attr_hash_id char(32) NOT NULL DEFAULT '',
  PRIMARY KEY (id,peer_hash_id,timestamp),
  KEY idx_prefix (prefix,prefix_len),
  KEY idx_rd (rd),
  KEY idx_type (type),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
  PARTITION BY RANGE  COLUMNS(timestamp)
SUBPARTITION BY KEY (peer_hash_id)
SUBPARTITIONS 32
(PARTITION p2017_06 VALUES LESS THAN ('2017-07-01') ENGINE = InnoDB,
 PARTITION p2017_07 VALUES LESS THAN ('2017-08-01') ENGINE = InnoDB,
 PARTITION p2017_08 VALUES LESS THAN ('2017-09-01') ENGINE = InnoDB,
 PARTITION p2017_09 VALUES LESS THAN ('2017-10-01') ENGINE = InnoDB,
 PARTITION p2017_10 VALUES LESS THAN ('2017-11-01') ENGINE = InnoDB,
 PARTITION p2017_11 VALUES LESS THAN ('2017-12-01') ENGINE = InnoDB,
 PARTITION p2017_12 VALUES LESS THAN ('2018-01-01') ENGINE = InnoDB,
 PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);


# Create l3vpn_rib table
CREATE TABLE l3vpn_rib (
  hash_id char(32) NOT NULL,
  path_attr_hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  isIPv4 tinyint(4) NOT NULL,
  origin_as int(10) unsigned NOT NULL,
  rd varchar(30) NOT NULL,
  prefix varchar(40) NOT NULL,
  prefix_len int(10) unsigned NOT NULL,
  prefix_bin varbinary(16) NOT NULL,
  prefix_bcast_bin varbinary(16) NOT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  first_added_timestamp timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  prefix_bits varchar(128) DEFAULT NULL,
  path_id int(10) unsigned DEFAULT NULL,
  labels varchar(255) DEFAULT NULL,
  isPrePolicy tinyint(4) NOT NULL DEFAULT '1',
  isAdjRibIn tinyint(4) NOT NULL DEFAULT '1',
  PRIMARY KEY (hash_id,peer_hash_id,isPrePolicy,isAdjRibIn),
  KEY idx_peer_id (peer_hash_id),
  KEY idx_path_id (path_attr_hash_id),
  KEY idx_prefix (prefix),
  KEY idx_rd (rd),
  KEY idx_prefix_len (prefix_len),
  KEY idx_prefix_bin (prefix_bin),
  KEY idx_addr_type (isIPv4),
  KEY idx_isWithdrawn (isWithdrawn),
  KEY idx_origin_as (origin_as),
  KEY idx_ts (timestamp),
  KEY idx_prefix_bits (prefix_bits),
  KEY idx_first_added_ts (first_added_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC
  PARTITION BY KEY (peer_hash_id) PARTITIONS 48;

# Create l3vpn rib view
drop view v_l3vpn_routes;
CREATE  VIEW v_l3vpn_routes AS
       SELECT  if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName,
                if(length(p.name) > 0, p.name, p.peer_addr) AS PeerName,
                r.rd as RD,
                r.prefix AS Prefix,r.prefix_len AS PrefixLen,
                path.origin AS Origin,r.origin_as AS Origin_AS,path.med AS MED,
                path.local_pref AS LocalPref,path.next_hop AS NH,path.as_path AS AS_Path,
                path.as_path_count AS ASPath_Count,path.community_list AS Communities,
                path.ext_community_list AS ExtCommunities,path.cluster_list AS ClusterList,
                path.aggregator AS Aggregator,p.peer_addr AS PeerAddress, p.peer_as AS PeerASN,r.isIPv4 as isIPv4,
                p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN,
                r.timestamp AS LastModified, r.first_added_timestamp as FirstAddedTimestamp,r.prefix_bin as prefix_bin,
                r.path_id, r.labels,
                r.hash_id as rib_hash_id,
                r.path_attr_hash_id as path_hash_id, r.peer_hash_id, rtr.hash_id as router_hash_id,r.isWithdrawn,
                r.prefix_bits,r.isPrePolicy,r.isAdjRibIn
        FROM bgp_peers p JOIN l3vpn_rib r ON (r.peer_hash_id = p.hash_id)
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
       WHERE r.isWithdrawn = False;

# Create l3vpn rib trigger
drop trigger l3vpn_rib_pre_update;
delimiter //
CREATE TRIGGER l3vpn_rib_pre_update BEFORE UPDATE on l3vpn_rib
  FOR EACH ROW
  BEGIN
      # Allow per session disabling of trigger (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
      IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

        # Make sure we are updating a duplicate
        IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
            IF (new.isWithdrawn = False) THEN
              IF (old.path_attr_hash_id != new.path_attr_hash_id AND old.path_attr_hash_id != '') THEN
                   # Add path log if the path has changed
                    INSERT IGNORE INTO l3vpn_log (type,rd,prefix,prefix_len,path_attr_hash_id,peer_hash_id,timestamp)
                                VALUES ('changed', old.rd, old.prefix,old.prefix_len,old.path_attr_hash_id,old.peer_hash_id,
                                        new.timestamp);
              END IF;

              # Update first_added_timestamp if withdrawn for a long timestamp
              IF (old.isWithdrawn = True AND old.timestamp < date_sub(new.timestamp, INTERVAL 6 HOUR)) THEN
                  SET new.first_added_timestamp = current_timestamp(6);
              END IF;

            ELSE
                # Add log entry for withdrawn prefix
                INSERT IGNORE INTO l3vpn_log
                       (type,rd,prefix,prefix_len,peer_hash_id,path_attr_hash_id,timestamp)
                           VALUES ('withdrawn', old.rd, old.prefix,old.prefix_len,old.peer_hash_id,
                                   old.path_attr_hash_id,new.timestamp);
            END IF;       
            
        END IF;
      END IF;
  END;//
delimiter ;       


UPGRADE
```
