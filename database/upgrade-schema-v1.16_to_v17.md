DB Alter Changes for v1.17
--------------------------------

It is **recommended** that you replace/REINIT your DB with the latest
changes as they can take a while to migrate.

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-v1.17.db
    mysql -u root -p openBMP < mysql-openbmp-current.db


### Alternative: Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> openBMP <<UPGRADE

# Add new fields to peer table
alter table bgp_peers add column isLocRib tinyint(4) NOT NULL DEFAULT '0',
    add column isLocRibFiltered tinyint(4) NOT NULL DEFAULT '0',
     add column table_name varchar(255) DEFAULT NULL;


# Add columns to peer VIEW
drop view v_peers;
CREATE VIEW v_peers AS
SELECT if (length(rtr.name) > 0, rtr.name, rtr.ip_address) AS RouterName, rtr.ip_address as RouterIP,
                p.local_ip as LocalIP, p.local_port as LocalPort, p.local_asn as LocalASN, p.local_bgp_id as LocalBGPId,
                if(length(p.name) > 0, p.name, p.peer_addr) AS `PeerName`,
                p.peer_addr as PeerIP, p.remote_port as PeerPort, p.peer_as as PeerASN, 
                p.peer_bgp_id as PeerBGPId, 
                p.local_hold_time as LocalHoldTime, p.remote_hold_time as PeerHoldTime,
                p.state as isUp, rtr.isConnected as isBMPConnected,
                p.isIPv4 as isPeerIPv4, p.isL3VPNpeer as isPeerVPN, p.isPrePolicy as isPrePolicy,
                p.timestamp as LastModified,
                p.bmp_reason as LastBMPReasonCode, p.bgp_err_code as LastDownCode,
                p.bgp_err_subcode as LastdownSubCode, p.error_text as LastDownMessage,
                p.timestamp as LastDownTimestamp,
                p.sent_capabilities as SentCapabilities, p.recv_capabilities as RecvCapabilities,
                w.as_name,
                p.isLocRib,p.isLocRibFiltered,p.table_name,
                p.hash_id as peer_hash_id, rtr.hash_id as router_hash_id,p.geo_ip_start

        FROM bgp_peers p JOIN routers rtr ON (p.router_hash_id = rtr.hash_id) 
                                         LEFT JOIN gen_whois_asn w ON (p.peer_as = w.asn)

UPGRADE
```
