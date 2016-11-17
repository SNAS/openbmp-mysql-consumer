DB Alter Changes for v1.14 v1.15
--------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.15.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE

    # MsgBus schema 1.4 updates
    alter table ls_nodes add column sr_capabilities varchar(255);
    alter table ls_links add column sr_adjacency_sids varchar(255);
    alter table ls_prefixes add column sr_prefix_sids varchar(255);

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
             ls_nodes.sr_capabilities,
             ls_nodes.hash_id,ls_nodes.path_attr_hash_id,ls_nodes.peer_hash_id,r.hash_id as router_hash_id             
          FROM ls_nodes LEFT JOIN path_attrs ON (ls_nodes.path_attr_hash_id = path_attrs.hash_id AND ls_nodes.peer_hash_id = path_attrs.peer_hash_id) 
    	    JOIN ls_links links ON (ls_nodes.hash_id = links.local_node_hash_id and links.isWithdrawn = False)
                JOIN bgp_peers p on (p.hash_id = ls_nodes.peer_hash_id) JOIN
                                 routers r on (p.router_hash_id = r.hash_id)
             WHERE not ls_nodes.igp_router_id regexp "\..[1-9A-F]00$" AND ls_nodes.igp_router_id not like "%]" and ls_nodes.iswithdrawn = False 
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
          unreserved_bw,te_def_metric,mpls_proto_mask,srlg,ln.name,
          ln.sr_adjacency_sids,localn.sr_capabilities, 
          ln.timestamp,local_node_hash_id,remote_node_hash_id,
          localn.igp_router_id as localn_igp_router_id_bin,remoten.igp_router_id as remoten_igp_router_id_bin,
          ln.path_attr_hash_id as path_attr_hash_id, ln.peer_hash_id as peer_hash_id
      FROM ls_links ln JOIN ls_nodes localn ON (ln.local_node_hash_id = localn.hash_id 
                AND ln.peer_hash_id = localn.peer_hash_id and localn.iswithdrawn = False)
             JOIN ls_nodes remoten ON (ln.remote_node_hash_id = remoten.hash_id
                AND ln.peer_hash_id = remoten.peer_hash_id and remoten.iswithdrawn = False)
    	WHERE ln.isWithdrawn = False;

    drop view v_ls_prefixes;
    CREATE VIEW v_ls_prefixes AS
    SELECT localn.name as Local_Router_Name,localn.igp_router_id as Local_IGP_RouterId,
             localn.router_id as Local_RouterId, 
             lp.id,lp.mt_id,prefix as Prefix, prefix_len,ospf_route_type,metric,lp.protocol,
             lp.sr_prefix_sids, localn.sr_capabilities,
             lp.timestamp,lp.prefix_bcast_bin,lp.prefix_bin,
             lp.peer_hash_id
        FROM ls_prefixes lp JOIN ls_nodes localn ON (lp.local_node_hash_id = localn.hash_id)
        WHERE lp.isWithdrawn = False;
    

UPGRADE
```
