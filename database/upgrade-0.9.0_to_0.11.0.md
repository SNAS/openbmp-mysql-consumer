DB Alter Changes for 0.11.0 from 0.9.0
--------------------------------------
This is the first release with Kafka integration and therefore it is recommended to start with a fresh database.  

### Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> <<DB_CREATE

   CREATE DATABASE openBMPold
   grant all on openBMP.* to 'openbmp'@'localhost';
   grant all on openBMP.* to 'openbmp'@'%';
   
   drop trigger ins_bgp_peers;
   drop trigger upd_bgp_peers;
   drop trigger ins_gen_asn_stats;
   drop trigger rib_pre_update;
   drop trigger upd_routers;
    
   RENAME TABLE openBMP.as_path_analysis TO openBMPold.as_path_analsysis;
   RENAME TABLE openBMP.bgp_peers TO openBMPold.bgp_peers;
   RENAME TABLE openBMP.gen_asn_stats TO openBMPold.gen_asn_stats;
   RENAME TABLE openBMP.gen_whois_asn TO openBMPold.gen_whois_asn; 
   RENAME TABLE openBMP.gen_whois_route TO openBMPold.gen_whois_route;
   RENAME TABLE openBMP.ls_links TO openBMPold.ls_links;
   RENAME TABLE openBMP.ls_nodes TO openBMPold.ls_nodes;
   RENAME TABLE openBMP.ls_prefixes TO openBMPold.ls_prefixes;
   RENAME TABLE openBMP.path_attr_log TO openBMPold.path_attr_log;
   RENAME TABLE openBMP.path_attrs TO openBMPold.path_attrs;
   RENAME TABLE openBMP.peer_down_events TO openBMPold.peer_down_events;
   RENAME TABLE openBMP.peer_up_events TO openBMPold.peer_up_events;
   RENAME TABLE openBMP.rib TO openBMPold.rib;
   RENAME TABLE openBMP.stat_reports TO openBMPold.stat_reports;
   RENAME TABLE openBMP.withdrawn_log TO openBMPold.withdrawn_log;
DB_CREATE
```

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-current.db
    mysql -u root -p openBMP < mysql-openbmp-current.db

