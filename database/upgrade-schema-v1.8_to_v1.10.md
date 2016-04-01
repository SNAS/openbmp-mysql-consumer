DB Alter Changes for v1.8 v1.10
-------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.10.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE
    alter table routers add column collector_hash_id char(32) NOT NULL;

     alter table as_path_analysis drop primary key,
            add primary key (asn, path_attr_hash_id, peer_hash_id);

     alter table as_path_analysis partition BY KEY (peer_hash_id) partitions 48;

     drop trigger rib_pre_update;
      delimiter //
      CREATE TRIGGER rib_pre_update BEFORE UPDATE on rib
        FOR EACH ROW
            # Allow per session disabling of trigger
            # (set @TRIGGER_DISABLED=TRUE to disable, set @TRIGGER_DISABLED=FALSE to enable)
            IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

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
              END IF;
            END IF //
      delimiter ;

      CREATE TABLE geo_location (
        country char(2) NOT NULL,
        city varchar(50) NOT NULL,
        latitude float DEFAULT NULL,
        longitude float DEFAULT NULL,
        PRIMARY KEY (country,city)
      ) ENGINE=InnoDB
      
      CREATE TABLE bgp_nexthop (
          nexthop varchar(46) NOT NULL,
          ls_prefix_hash_id char(32) DEFAULT NULL,
          ls_prefix varchar(46) DEFAULT NULL,
          ls_prefix_len tinyint(3) unsigned DEFAULT NULL,
          ls_src_node_hash_id char(32) DEFAULT NULL,
          ls_peer_hash_id char(32) DEFAULT NULL,
          ls_area_id varchar(46) DEFAULT NULL,
          ls_metric int(11) DEFAULT NULL,
          timestamp timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
          PRIMARY KEY (nexthop),
          KEY idx_prefix_hash_id (ls_prefix_hash_id) USING BTREE,
          KEY idx_nexthop (nexthop) USING BTREE
        ) ENGINE=MEMORY DEFAULT CHARSET=latin1;
        
        CREATE TABLE unicast_rib_lookup (
          prefix_bin varbinary(16) NOT NULL,
          prefix_bcast_bin varbinary(16) NOT NULL,
          prefix_len int(8) unsigned NOT NULL,
          origin_as int(10) unsigned NOT NULL,
          isIPv4 bit(1) NOT NULL,
          isLS bit(1) NOT NULL,
          refCount int(11) NOT NULL,
          PRIMARY KEY (prefix_bin,prefix_len),
          KEY idx_prefix (prefix_bin) USING BTREE,
          KEY idx_prefix_bcast (prefix_bcast_bin) USING BTREE,
          KEY idx_prefix_range (prefix_bcast_bin,prefix_bin) USING BTREE
        ) ENGINE=MEMORY DEFAULT CHARSET=latin1;
        
        drop trigger if exists upd_bgp_peers;
        delimiter //
        CREATE TRIGGER upd_bgp_peers BEFORE UPDATE ON bgp_peers
        FOR EACH ROW
            BEGIN
                declare geo_ip_start varbinary(16);
            
            SELECT ip_start INTO geo_ip_start
                FROM geo_ip
                WHERE ip_end >= inet6_aton(new.peer_addr)
                       and ip_start <= inet6_aton(new.peer_addr) and addr_type = if (new.isIPv4 = 1, 'ipv4', 'ipv6')
                 ORDER BY ip_end limit 1;
        
            set new.geo_ip_start = geo_ip_start;
        
            END;//
        delimiter ;
        
        alter table ls_links change column mt_id mt_id int unsigned default '0';
        alter table ls_prefixes change column mt_id mt_id int unsigned default '0';
        
        drop view v_ls_nodes;
        CREATE VIEW v_ls_nodes AS
        SELECT r.name as RouterName,r.ip_address as RouterIP,
               p.name as PeerName, p.peer_addr as PeerIP,igp_router_id as IGP_RouterId,
            ls_nodes.name as NodeName,
                 if (ls_nodes.protocol like 'OSPF%', igp_router_id, router_id) as RouterId,
                 ls_nodes.id, ls_nodes.bgp_ls_id as bgpls_id, ls_nodes.ospf_area_id as OspfAreaId, 
                 ls_nodes.isis_area_id as ISISAreaId, ls_nodes.protocol, flags, ls_nodes.timestamp,
                 ls_nodes.asn,path_attrs.as_path as AS_Path,path_attrs.local_pref as LocalPref,
                 path_attrs.med as MED,path_attrs.next_hop as NH,links.mt_id,
                 ls_nodes.hash_id,ls_nodes.path_attr_hash_id,ls_nodes.peer_hash_id,r.hash_id as router_hash_id
              FROM ls_nodes LEFT JOIN path_attrs ON (ls_nodes.path_attr_hash_id = path_attrs.hash_id AND ls_nodes.peer_hash_id = path_attrs.peer_hash_id) 
                JOIN ls_links links ON (ls_nodes.hash_id = links.local_node_hash_id)
                    JOIN bgp_peers p on (p.hash_id = ls_nodes.peer_hash_id) JOIN
                    routers r on (p.router_hash_id = r.hash_id)
            GROUP BY ls_nodes.hash_id,links.mt_id;
        
        drop view v_ls_links;
        CREATE VIEW v_ls_links AS
        SELECT localn.name as Local_Router_Name,remoten.name as Remote_Router_Name,
                 localn.igp_router_id as Local_IGP_RouterId,localn.router_id as Local_RouterId, 
                 remoten.igp_router_id Remote_IGP_RouterId, remoten.router_id as Remote_RouterId, 
                 localn.bgp_ls_id as bgpls_id, 
                 IF (ln.protocol in ('OSPFv2', 'OSPFv3'),localn.ospf_area_id, localn.isis_area_id) as AreaId,
              ln.mt_id as MT_ID,interface_addr as InterfaceIP,neighbor_addr as NeighborIP,
              ln.isIPv4,ln.protocol,igp_metric,local_link_id,remote_link_id,admin_group,max_link_bw,max_resv_bw,
              unreserved_bw,te_def_metric,mpls_proto_mask,srlg,ln.name,ln.timestamp,local_node_hash_id,remote_node_hash_id,
              localn.igp_router_id as localn_igp_router_id_bin,remoten.igp_router_id as remoten_igp_router_id_bin,
              ln.path_attr_hash_id as path_attr_hash_id, ln.peer_hash_id as peer_hash_id
          FROM ls_links ln JOIN ls_nodes localn ON (ln.local_node_hash_id = localn.hash_id 
                    AND ln.peer_hash_id = localn.peer_hash_id)
                 JOIN ls_nodes remoten ON (ln.remote_node_hash_id = remoten.hash_id
                    AND ln.peer_hash_id = remoten.peer_hash_id);
        
        drop view v_ls_prefixes;
        CREATE VIEW v_ls_prefixes AS
        SELECT localn.name as Local_Router_Name,localn.igp_router_id as Local_IGP_RouterId,
                 localn.router_id as Local_RouterId, 
                 lp.id,lp.mt_id,prefix as Prefix, prefix_len,ospf_route_type,metric,lp.protocol,
                 lp.timestamp,lp.prefix_bcast_bin,lp.prefix_bin,
                 lp.peer_hash_id
            FROM ls_prefixes lp JOIN ls_nodes localn ON (lp.local_node_hash_id = localn.hash_id);



UPGRADE
```
