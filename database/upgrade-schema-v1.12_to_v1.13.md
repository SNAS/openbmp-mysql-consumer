DB Alter Changes for v1.12 v1.13
--------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.13.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE
    ALTER TABLE ls_nodes add column mt_ids varchar(128);
    ALTER TABLE ls_links change column max_link_bw max_link_bw int(10) unsigned default 0;
    ALTER TABLE ls_links change column max_resv_bw max_resv_bw int(10) unsigned default 0;

    ALTER TABLE rib CHANGE db_timestamp first_added_timestamp timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    ALTER TABLE rib ADD KEY idx_first_added_ts (first_added_timestamp);
    ALTER TABLE rib ADD COLUMN path_id int(10) unsigned, ADD COLUMN labels varchar(255);


drop view v_routes;
CREATE  VIEW v_routes AS
       SELECT  if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName,
                if(length(p.name) > 0, p.name, p.peer_addr) AS PeerName,
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
                r.prefix_bits
        FROM bgp_peers p JOIN rib r ON (r.peer_hash_id = p.hash_id)
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
       WHERE r.isWithdrawn = False;

drop view v_all_routes;
CREATE  VIEW v_all_routes AS
       SELECT  if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName,
                if(length(p.name) > 0, p.name, p.peer_addr) AS PeerName,
                r.prefix AS Prefix,r.prefix_len AS PrefixLen,
                path.origin AS Origin,r.origin_as AS Origin_AS,path.med AS MED,
                path.local_pref AS LocalPref,path.next_hop AS NH,path.as_path AS AS_Path,
                path.as_path_count AS ASPath_Count,path.community_list AS Communities,
                path.ext_community_list AS ExtCommunities,path.cluster_list AS ClusterList,
                path.aggregator AS Aggregator,p.peer_addr AS PeerAddress, p.peer_as AS PeerASN,r.isIPv4 as isIPv4,
                p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN,
                r.timestamp AS LastModified,r.first_added_timestamp as FirstAddedTimestamp,r.prefix_bin as prefix_bin,
                r.path_id, r.labels,
                r.hash_id as rib_hash_id,
                r.path_attr_hash_id as path_hash_id, r.peer_hash_id, rtr.hash_id as router_hash_id,r.isWithdrawn,
                r.prefix_bits
        FROM bgp_peers p JOIN rib r ON (r.peer_hash_id = p.hash_id)
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id);


    drop trigger rib_pre_update;
    delimiter //
    CREATE TRIGGER rib_pre_update BEFORE UPDATE on rib
      FOR EACH ROW
      BEGIN
          # Allow per session disabling of trigger (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
          IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

            # Make sure we are updating a duplicate
            IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
                IF (new.isWithdrawn = False AND old.path_attr_hash_id != new.path_attr_hash_id AND old.path_attr_hash_id != '') THEN
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
                ELSEIF (new.isWithdrawn = True) THEN
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


UPGRADE
```
