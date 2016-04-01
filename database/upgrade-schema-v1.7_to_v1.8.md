DB Alter Changes for v1.7 to v1.8
---------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate. 

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.8.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> <<UPGRADE

    CREATE TABLE `community_analysis` (
      `community` varchar(22) NOT NULL,
      `part1` int(10) unsigned NOT NULL DEFAULT '0',
      `part2` int(10) unsigned NOT NULL DEFAULT '0',
      `path_attr_hash_id` char(32) NOT NULL,
      `peer_hash_id` char(32) NOT NULL,
      `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (`community`,`peer_hash_id`,`path_attr_hash_id`),
      KEY `idx_community` (`community`),
      KEY `idx_part1` (`part1`),
      KEY `idx_part2` (`part2`),
      KEY `idx_peer_hash` (`peer_hash_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=latin1 PARTITION BY KEY (peer_hash_id) PARTITIONS 48;

    ALTER TABLE as_path_analysis ADD INDEX idx_peer_asn (peer_hash_id, asn);

    ALTER TABLE path_attr_log drop column rib_hash_id,
        add column prefix varchar(46) NOT NULL, 
        add column prefix_len tinyint(3) unsigned NOT NULL,
        add index idx_prefix_full (prefix,prefix_len),
        add index idx_prefix (prefix);
        
    ALTER TABLE rib PARTITION BY KEY (peer_hash_id) PARTITIONS 48;
    
    drop trigger rib_pre_update;
    delimiter //
    CREATE TRIGGER rib_pre_update BEFORE UPDATE on rib
         FOR EACH ROW
    
         # Make sure we are updating a duplicate
         IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
              IF (new.isWithdrawn = False AND old.path_attr_hash_id != new.path_attr_hash_id) THEN
                 # Add path log if the path has changed
                  INSERT IGNORE INTO path_attr_log (prefix,prefix_len,path_attr_hash_id,peer_hash_id,timestamp) 
                              VALUES (old.prefix,old.prefix_len,old.path_attr_hash_id,old.peer_hash_id,
                                      new.timestamp);
    
              ELSEIF (new.isWithdrawn = True) THEN
                 # Add log entry for withdrawn prefix
                  INSERT IGNORE INTO withdrawn_log
                          (prefix,prefix_len,peer_hash_id,path_attr_hash_id,timestamp) 
                              VALUES (old.prefix,old.prefix_len,old.peer_hash_id,
                                      old.path_attr_hash_id,new.timestamp);
                 
              END IF;       
          END IF //
    delimiter ;

    drop view v_routes_history;
    CREATE VIEW v_routes_history AS
      SELECT if(length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName, 
                    if(length(p.name) > 0, p.name, p.peer_addr) AS PeerName,
                    pathlog.prefix AS Prefix,pathlog.prefix_len AS PrefixLen,
                    path.origin AS Origin,path.origin_as AS Origin_AS,
                        path.med AS MED,path.local_pref AS LocalPref,path.next_hop AS NH,
                    path.as_path AS AS_Path,path.as_path_count AS ASPath_Count,path.community_list AS Communities,
                            path. ext_community_list AS ExtCommunities,
                    path.cluster_list AS ClusterList,path.aggregator AS Aggregator,p.peer_addr AS PeerAddress,
                    p.peer_as AS PeerASN,  p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN,
                    pathlog.id,pathlog.timestamp AS LastModified,
                   pathlog.path_attr_hash_id as path_attr_hash_id, pathlog.peer_hash_id, rtr.hash_id as router_hash_id
            FROM path_attr_log pathlog 
                     STRAIGHT_JOIN path_attrs path 
                                     ON (pathlog.path_attr_hash_id = path.hash_id AND
                                             pathlog.peer_hash_id = path.peer_hash_id)
                     STRAIGHT_JOIN bgp_peers p ON (pathlog.peer_hash_id = p.hash_id) 
    	         STRAIGHT_JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
            ORDER BY id Desc;

    drop view v_ls_nodes;
    CREATE VIEW v_ls_nodes AS
    SELECT r.name as RouterName,r.ip_address as RouterIP,
           p.name as PeerName, p.peer_addr as PeerIP,igp_router_id as IGP_RouterId,
             if (protocol like 'OSPF%', igp_router_id, router_id) as RouterId,
             id, bgp_ls_id as bgpls_id, ospf_area_id as OspfAreaId, 
             isis_area_id as ISISAreaId, protocol, flags, ls_nodes.timestamp,
             ls_nodes.asn,path_attrs.as_path as AS_Path,path_attrs.local_pref as LocalPref,
             path_attrs.med as MED,path_attrs.next_hop as NH,
             ls_nodes.hash_id,ls_nodes.path_attr_hash_id,ls_nodes.peer_hash_id,r.hash_id as router_hash_id
          FROM ls_nodes LEFT JOIN path_attrs ON (ls_nodes.path_attr_hash_id = path_attrs.hash_id AND ls_nodes.peer_hash_id = path_attrs.peer_hash_id) 
                JOIN bgp_peers p on (p.hash_id = ls_nodes.peer_hash_id) JOIN
                routers r on (p.router_hash_id = r.hash_id);

    drop view v_routes_withdraws;
    CREATE VIEW v_routes_withdraws AS
    SELECT if((length(rtr.name) > 0),rtr.name,rtr.ip_address) AS RouterName,if((length(p.name) > 0),p.name,p.peer_addr) AS PeerName,
            log.prefix AS Prefix,log.prefix_len AS PrefixLen,
            path.origin AS Origin,path.origin_as AS Origin_AS,path.med AS MED,path.local_pref AS LocalPref,
            path.next_hop AS NH,path.as_path AS AS_Path,path.as_path_count AS ASPath_Count,
            path.community_list AS Communities,path.ext_community_list AS ExtCommunities,path.cluster_list AS ClusterList,
            path.aggregator AS Aggregator,p.peer_addr AS PeerAddress,p.peer_as AS PeerASN,
            p.isIPv4 AS isPeerIPv4,p.isL3VPNpeer AS isPeerVPN,log.id AS id,log.timestamp AS LastModified,
            log.path_attr_hash_id AS path_attr_hash_id,log.peer_hash_id AS peer_hash_id,rtr.hash_id AS router_hash_id 
        FROM withdrawn_log log JOIN bgp_peers p ON (log.peer_hash_id = p.hash_id)
             JOIN path_attrs path ON (path.hash_id = log.path_attr_hash_id)
             JOIN routers rtr ON (p.router_hash_id = rtr.hash_id) 
        ORDER BY log.timestamp desc;

UPGRADE
```




