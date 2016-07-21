DB Alter Changes for v1.13 v1.14
--------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.14.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE
    # Add bgp ID to routers (api version 1.2)
    alter table routers add column bgp_id varchar(46);
    
    # Add EPE support (api version 1.2)
    alter table ls_links change column protocol protocol enum('IS-IS_L1','IS-IS_L2','OSPFv2','Direct','Static','OSPFv3','EPE','') DEFAULT NULL,
                add column local_igp_router_id varchar(46) NOT NULL,
                add column local_router_id varchar(46) NOT NULL,
                add column local_asn int unsigned not null,
                add column remote_igp_router_id varchar(46) NOT NULL,
                add column remote_router_id varchar(46) NOT NULL,
                add column remote_asn int unsigned not null,
                add column peer_node_sid varchar(128) not null;

    # Update RIB to support pre/post policy entries as well as adj-rib-in and adj-rib-out. 
    alter table rib add column isPrePolicy tinyint(4) DEFAULT '1', add column isAdjRibIn tinyint(4) DEFAULT '1',
             drop primary key, add primary key (hash_id,peer_hash_id,isPrePolicy,isAdjRibIn);

    # Updated to support pseudo peer
    drop trigger if exists ins_bgp_peers;
    delimiter //
    CREATE TRIGGER ins_bgp_peers BEFORE INSERT ON bgp_peers
    FOR EACH ROW
        BEGIN
            declare geo_ip_start varbinary(16);
            declare routerName varchar(128);
            declare routerIP varchar(46);


        IF (new.peer_addr = "0.0.0.0" AND new.peer_bgp_id = "0.0.0.0") THEN
             SELECT r.name,r.ip_address INTO routerName,routerIP FROM routers r where new.router_hash_id = r.hash_id;
             set new.name = routerName;

            SELECT ip_start INTO geo_ip_start
               FROM geo_ip
               WHERE ip_end >= inet6_aton(routerIP)
                      and ip_start <= inet6_aton(routerIP) and addr_type = if (routerIP like "%.%", 'ipv4', 'ipv6')
                ORDER BY ip_end limit 1;

        ELSE
           SELECT ip_start INTO geo_ip_start
               FROM geo_ip
               WHERE ip_end >= inet6_aton(new.peer_addr)
                      and ip_start <= inet6_aton(new.peer_addr) and addr_type = if (new.isIPv4 = 1, 'ipv4', 'ipv6')
                ORDER BY ip_end limit 1;

        END IF;

        set new.geo_ip_start = geo_ip_start;

        # Insert Peer Down Event
        IF (new.state = 0) THEN
            INSERT IGNORE INTO peer_down_events (peer_hash_id,bmp_reason,bgp_err_code,bgp_err_subcode,error_text,timestamp)
                VALUES (new.hash_id,new.bmp_reason,new.bgp_err_code,new.bgp_err_subcode,new.error_text,new.timestamp);

        ELSE
            INSERT IGNORE INTO peer_up_events (peer_hash_id,local_ip,local_bgp_id,local_port,local_hold_time,local_asn,remote_port,remote_hold_time,
                         sent_capabilities,recv_capabilities,timestamp)
                VALUES (new.hash_id,new.local_ip,new.local_bgp_id,new.local_port,new.local_hold_time,new.local_asn,new.remote_port,new.remote_hold_time,
                      new.sent_capabilities,new.recv_capabilities,new.timestamp);
        END IF;

        END;//
    delimiter ;

    # Updated to support pseudo peer
    drop trigger if exists upd_bgp_peers;
    delimiter //
    CREATE TRIGGER upd_bgp_peers BEFORE UPDATE ON bgp_peers
    FOR EACH ROW
        BEGIN
            declare geo_ip_start varbinary(16);
            declare routerName varchar(128);
            declare routerIP varchar(46);

        IF (new.peer_addr = "0.0.0.0" AND new.peer_bgp_id = "0.0.0.0") THEN
             SELECT r.name,r.ip_address INTO routerName,routerIP FROM routers r where new.router_hash_id = r.hash_id;
             set new.name = routerName;

            SELECT ip_start INTO geo_ip_start
               FROM geo_ip
               WHERE ip_end >= inet6_aton(routerIP)
                      and ip_start <= inet6_aton(routerIP) and addr_type = if (routerIP like "%.%", 'ipv4', 'ipv6')
                ORDER BY ip_end limit 1;

        ELSE
           SELECT ip_start INTO geo_ip_start
               FROM geo_ip
               WHERE ip_end >= inet6_aton(new.peer_addr)
                      and ip_start <= inet6_aton(new.peer_addr) and addr_type = if (new.isIPv4 = 1, 'ipv4', 'ipv6')
                ORDER BY ip_end limit 1;

        END IF;

        set new.geo_ip_start = geo_ip_start;

        END;//
    delimiter ;


    # Below adds an update to the first_added_timestamp if the time is too old (refreshes the first_added_timestamp)
    drop trigger rib_pre_update;
    delimiter //
    CREATE TRIGGER rib_pre_update BEFORE UPDATE on rib
      FOR EACH ROW
      BEGIN
          # Allow per session disabling of trigger (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
          IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

            # Make sure we are updating a duplicate
            IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
                IF (new.isWithdrawn = False) THEN
                  IF (old.path_attr_hash_id != new.path_attr_hash_id AND old.path_attr_hash_id != '') THEN
                       # Add path log if the path has changed
                        INSERT IGNORE INTO path_attr_log (prefix,prefix_len,path_attr_hash_id,peer_hash_id,timestamp)
                                    VALUES (old.prefix,old.prefix_len,old.path_attr_hash_id,old.peer_hash_id,
                                            new.timestamp);

                      # Update gen_prefix_validation table (RPKI/IRR)
                      IF (new.origin_as > 0 and new.origin_as != 23456) THEN
                         INSERT IGNORE INTO gen_prefix_validation (prefix,prefix_len,recv_origin_as,rpki_origin_as,irr_origin_as,irr_source)

                              SELECT SQL_SMALL_RESULT new.prefix_bin,new.prefix_len,new.origin_as,
                                                           rpki.origin_as, w.origin_as,w.source
                                       FROM (SELECT new.prefix_bin as prefix_bin, new.prefix_len as prefix_len, new.origin_as as origin_as) rib
                                                 LEFT JOIN gen_whois_route w ON (new.prefix_bin = w.prefix AND
                                                            new.prefix_len = w.prefix_len)
                                                 LEFT JOIN rpki_validator rpki ON (new.prefix_bin = rpki.prefix AND
                                                            new.prefix_len >= rpki.prefix_len and new.prefix_len <= rpki.prefix_len_max)

                               ON DUPLICATE KEY UPDATE rpki_origin_as = values(rpki_origin_as),
                                                                           irr_origin_as=values(irr_origin_as),irr_source=values(irr_source);
                      END IF;
                  END IF;

                  # Update first_added_timestamp if withdrawn for a long timestamp
                  IF (old.isWithdrawn = True AND old.timestamp < date_sub(new.timestamp, INTERVAL 6 HOUR)) THEN
                      SET new.first_added_timestamp = current_timestamp(6);
                  END IF;

                ELSE
                   # Add log entry for withdrawn prefix
                    INSERT IGNORE INTO withdrawn_log
                            (prefix,prefix_len,peer_hash_id,path_attr_hash_id,timestamp)
                                VALUES (old.prefix,old.prefix_len,old.peer_hash_id,
                                        old.path_attr_hash_id,new.timestamp);
                END IF;       
                
            END IF;
          END IF;
      END;//
    delimiter ;

    #### Below is not needed - adds first_added_timestamp to path_attrs
    ALTER TABLE path_attrs ADD first_added_timestamp timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    ALTER TABLE path_attrs ADD KEY idx_first_added_ts (first_added_timestamp);

    # Resolve deadlock issues caused by bulk inserts and trigger updates to gen_prefix_validation
    #   Changing to memory seems to solve the deadlocks
    #alter table gen_prefix_validation add PARTITION BY KEY (recv_origin_as) PARTITIONS 48

UPGRADE
```
