DB Alter Changes for v1.11 v1.12
-------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.12.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE

    alter table path_attrs partition BY KEY (peer_hash_id) partitions 48;
    
    alter table stat_reports add index idx_peer_hash_id (peer_hash_id);

drop view v_routes;
CREATE  VIEW `v_routes` AS 
       SELECT  if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName, 
                if(length(p.name) > 0, p.name, p.peer_addr) AS `PeerName`,
                `r`.`prefix` AS `Prefix`,`r`.`prefix_len` AS `PrefixLen`,
                `path`.`origin` AS `Origin`,`r`.`origin_as` AS `Origin_AS`,`path`.`med` AS `MED`,
                `path`.`local_pref` AS `LocalPref`,`path`.`next_hop` AS `NH`,`path`.`as_path` AS `AS_Path`,
                `path`.`as_path_count` AS `ASPath_Count`,`path`.`community_list` AS `Communities`,
                `path`.`ext_community_list` AS `ExtCommunities`,`path`.`cluster_list` AS `ClusterList`,
                `path`.`aggregator` AS `Aggregator`,`p`.`peer_addr` AS `PeerAddress`, `p`.`peer_as` AS `PeerASN`,r.isIPv4 as isIPv4,
                 p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN,
                `r`.`timestamp` AS `LastModified`, r.db_timestamp as DBLastModified,r.prefix_bin as prefix_bin,
                 r.hash_id as rib_hash_id,
                 r.path_attr_hash_id as path_hash_id, r.peer_hash_id, rtr.hash_id as router_hash_id,r.isWithdrawn,
                 r.prefix_bits
        FROM bgp_peers p JOIN rib r ON (r.peer_hash_id = p.hash_id) 
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
       WHERE r.isWithdrawn = False;

CREATE  VIEW `v_all_routes` AS 
       SELECT  if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName, 
                if(length(p.name) > 0, p.name, p.peer_addr) AS `PeerName`,
                `r`.`prefix` AS `Prefix`,`r`.`prefix_len` AS `PrefixLen`,
                `path`.`origin` AS `Origin`,`r`.`origin_as` AS `Origin_AS`,`path`.`med` AS `MED`,
                `path`.`local_pref` AS `LocalPref`,`path`.`next_hop` AS `NH`,`path`.`as_path` AS `AS_Path`,
                `path`.`as_path_count` AS `ASPath_Count`,`path`.`community_list` AS `Communities`,
                `path`.`ext_community_list` AS `ExtCommunities`,`path`.`cluster_list` AS `ClusterList`,
                `path`.`aggregator` AS `Aggregator`,`p`.`peer_addr` AS `PeerAddress`, `p`.`peer_as` AS `PeerASN`,r.isIPv4 as isIPv4,
                 p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN,
                `r`.`timestamp` AS `LastModified`, r.db_timestamp as DBLastModified,r.prefix_bin as prefix_bin,
                 r.hash_id as rib_hash_id,
                 r.path_attr_hash_id as path_hash_id, r.peer_hash_id, rtr.hash_id as router_hash_id,r.isWithdrawn,
                 r.prefix_bits
        FROM bgp_peers p JOIN rib r ON (r.peer_hash_id = p.hash_id) 
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id);

UPGRADE
```
