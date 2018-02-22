-- -----------------------------------------------------------------------
-- BEGIN MySQL/MariaDB Schema
--     VERSION 1.22
-- -----------------------------------------------------------------------

--
-- Table structure for table collectors
--
DROP TABLE IF EXISTS collectors;
CREATE TABLE collectors (
  hash_id char(32) NOT NULL,
  state enum('up','down') DEFAULT NULL,
  admin_id varchar(64) NOT NULL,
  routers varchar(4096) DEFAULT NULL,
  router_count int(11) NOT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  name varchar(200) DEFAULT NULL,
  ip_address varchar(40) DEFAULT NULL,
  PRIMARY KEY (hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table routers
--
DROP TABLE IF EXISTS routers;
CREATE TABLE routers (
  hash_id char(32) NOT NULL,
  name varchar(200) NOT NULL,
  ip_address varchar(40) NOT NULL,
  router_AS int(10) unsigned DEFAULT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  description varchar(255) DEFAULT NULL,
  isConnected tinyint(4) DEFAULT 0,
  isPassive tinyint(4) DEFAULT 0,
  term_reason_code int(11) DEFAULT NULL,
  term_reason_text varchar(255) DEFAULT NULL,
  term_data mediumtext DEFAULT NULL,
  init_data mediumtext DEFAULT NULL,
  geo_ip_start varbinary(16) DEFAULT NULL,
  collector_hash_id char(32) NOT NULL,
  bgp_id varchar(46) DEFAULT NULL,
  PRIMARY KEY (hash_id),
  KEY idx_name (name),
  KEY idx_ip (ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC;

DELIMITER ;;
CREATE TRIGGER ins_routers BEFORE INSERT ON routers
FOR EACH ROW
  BEGIN
    declare geo_ip_start varbinary(16);

    SELECT ip_start INTO geo_ip_start
    FROM geo_ip
    WHERE ip_end >= inet6_aton(new.ip_address)
          and ip_start <= inet6_aton(new.ip_address) and
          addr_type = if (new.ip_address like "%:%", 'ipv6', 'ipv4')
    ORDER BY ip_end limit 1;

    set new.geo_ip_start = geo_ip_start;

  END;;

CREATE TRIGGER upd_routers BEFORE UPDATE ON routers
FOR EACH ROW
  BEGIN
    declare geo_ip_start varbinary(16);

    SELECT ip_start INTO geo_ip_start
    FROM geo_ip
    WHERE ip_end >= inet6_aton(new.ip_address)
          and ip_start <= inet6_aton(new.ip_address) and
          addr_type = if (new.ip_address like "%:%", 'ipv6', 'ipv4')
    ORDER BY ip_end limit 1;

    set new.geo_ip_start = geo_ip_start;

  END ;;
DELIMITER ;

--
-- Table structure for table bgp_peers
--
DROP TABLE IF EXISTS bgp_peers;
CREATE TABLE bgp_peers (
  hash_id char(32) NOT NULL,
  router_hash_id char(32) NOT NULL,
  peer_rd varchar(32) NOT NULL,
  isIPv4 tinyint(3) unsigned NOT NULL,
  peer_addr varchar(40) NOT NULL,
  name varchar(200) DEFAULT NULL,
  peer_bgp_id varchar(15) NOT NULL,
  peer_as int(10) unsigned NOT NULL,
  state tinyint(4) NOT NULL DEFAULT 1,
  isL3VPNpeer tinyint(4) NOT NULL DEFAULT 0,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  isPrePolicy tinyint(4) DEFAULT 1,
  geo_ip_start varbinary(16) DEFAULT NULL,
  local_ip varchar(40) NOT NULL,
  local_bgp_id varchar(15) NOT NULL,
  local_port int(10) unsigned NOT NULL,
  local_hold_time int(10) unsigned NOT NULL,
  local_asn int(10) unsigned NOT NULL,
  remote_port int(10) unsigned NOT NULL,
  remote_hold_time int(10) unsigned NOT NULL,
  sent_capabilities varchar(4096) DEFAULT NULL,
  recv_capabilities varchar(4096) DEFAULT NULL,
  bmp_reason tinyint(4) DEFAULT NULL,
  bgp_err_code int(10) unsigned DEFAULT NULL,
  bgp_err_subcode int(10) unsigned DEFAULT NULL,
  error_text varchar(255) DEFAULT NULL,
  isLocRib tinyint(4) NOT NULL DEFAULT 0,
  isLocRibFiltered tinyint(4) NOT NULL DEFAULT 0,
  table_name varchar(255) DEFAULT NULL,
  PRIMARY KEY (hash_id,router_hash_id),
  KEY idx_addr (peer_addr),
  KEY idx_name (name),
  KEY idx_main (peer_rd,peer_addr),
  KEY idx_as (peer_as),
  KEY idx_router_hash (router_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC;

DELIMITER ;;
CREATE TRIGGER ins_bgp_peers BEFORE INSERT ON bgp_peers
    FOR EACH ROW
        BEGIN
            declare geo_ip_start varbinary(16);
            declare routerName varchar(128);
            declare routerIP varchar(46);


        IF (new.peer_addr = "0.0.0.0" AND new.peer_bgp_id = "0.0.0.0") THEN
             SELECT r.name,r.ip_address INTO routerName,routerIP
                FROM routers r where new.router_hash_id = r.hash_id;
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

        
        IF (new.state = 0) THEN
            INSERT IGNORE INTO peer_down_events (peer_hash_id,bmp_reason,bgp_err_code,
                        bgp_err_subcode,error_text,timestamp)
                VALUES (new.hash_id,new.bmp_reason,new.bgp_err_code,new.bgp_err_subcode,new.error_text,new.timestamp);

        ELSE
            INSERT IGNORE INTO peer_up_events (peer_hash_id,local_ip,local_bgp_id,local_port,local_hold_time,
                         local_asn,remote_port,remote_hold_time,
                         sent_capabilities,recv_capabilities,timestamp)
                VALUES (new.hash_id,new.local_ip,new.local_bgp_id,new.local_port,new.local_hold_time,
                         new.local_asn,new.remote_port,new.remote_hold_time,
                         new.sent_capabilities,new.recv_capabilities,new.timestamp);
        END IF;

        END;;

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

        END ;;
DELIMITER ;

--
-- Table structure for table ls_nodes
--
DROP TABLE IF EXISTS ls_nodes;
CREATE TABLE ls_nodes (
  hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  path_attr_hash_id char(32) NOT NULL,
  id bigint(20) unsigned NOT NULL,
  asn int(10) unsigned NOT NULL,
  bgp_ls_id int(10) unsigned NOT NULL,
  igp_router_id varchar(46) NOT NULL,
  ospf_area_id varchar(16) NOT NULL,
  protocol enum('IS-IS_L1','IS-IS_L2','OSPFv2','Direct','Static','OSPFv3','') DEFAULT NULL,
  router_id varchar(46) NOT NULL,
  isis_area_id varchar(46) NOT NULL,
  flags varchar(20) NOT NULL,
  name varchar(255) NOT NULL,
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  mt_ids varchar(128) DEFAULT NULL,
  sr_capabilities varchar(255) DEFAULT NULL,
  PRIMARY KEY (hash_id,peer_hash_id),
  KEY idx_router_id (router_id),
  KEY idx_path_attr_hash_id (path_attr_hash_id),
  KEY idx_igp_router_id (igp_router_id),
  KEY idx_peer_id (peer_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
  PARTITION BY KEY (peer_hash_id);

--
-- Table structure for table ls_links
--
DROP TABLE IF EXISTS ls_links;
CREATE TABLE ls_links (
  hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  path_attr_hash_id char(32) NOT NULL,
  id bigint(20) unsigned NOT NULL,
  mt_id int(10) unsigned DEFAULT 0,
  interface_addr varchar(46) NOT NULL,
  neighbor_addr varchar(46) NOT NULL,
  isIPv4 tinyint(4) NOT NULL,
  protocol enum('IS-IS_L1','IS-IS_L2','OSPFv2','Direct','Static','OSPFv3','EPE','') DEFAULT NULL,
  local_link_id int(10) unsigned NOT NULL,
  remote_link_id int(10) unsigned NOT NULL,
  local_node_hash_id char(32) NOT NULL,
  remote_node_hash_id char(32) NOT NULL,
  admin_group int(11) NOT NULL,
  max_link_bw int(10) unsigned DEFAULT 0,
  max_resv_bw int(10) unsigned DEFAULT 0,
  unreserved_bw varchar(100) DEFAULT NULL,
  te_def_metric int(10) unsigned NOT NULL,
  protection_type varchar(60) DEFAULT NULL,
  mpls_proto_mask enum('LDP','RSVP-TE','') DEFAULT NULL,
  igp_metric int(10) unsigned NOT NULL,
  srlg varchar(128) NOT NULL,
  name varchar(255) NOT NULL,
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  local_igp_router_id varchar(46) NOT NULL,
  local_router_id varchar(46) NOT NULL,
  local_asn int(10) unsigned NOT NULL,
  remote_igp_router_id varchar(46) NOT NULL,
  remote_router_id varchar(46) NOT NULL,
  remote_asn int(10) unsigned NOT NULL,
  peer_node_sid varchar(128) NOT NULL,
  sr_adjacency_sids varchar(255) DEFAULT NULL,
  PRIMARY KEY (hash_id,peer_hash_id,local_node_hash_id),
  KEY idx_local_router_id (local_node_hash_id),
  KEY idx_path_attr_hash_id (path_attr_hash_id),
  KEY idx_remote_router_id (remote_node_hash_id),
  KEY idx_peer_id (peer_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
  PARTITION BY KEY (peer_hash_id);

--
-- Table structure for table ls_prefixes
--
DROP TABLE IF EXISTS ls_prefixes;
CREATE TABLE ls_prefixes (
  hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  path_attr_hash_id char(32) NOT NULL,
  id bigint(20) unsigned NOT NULL,
  local_node_hash_id char(32) NOT NULL,
  mt_id int(10) unsigned NOT NULL,
  protocol enum('IS-IS_L1','IS-IS_L2','OSPFv2','Direct','Static','OSPFv3','') DEFAULT NULL,
  prefix varchar(46) NOT NULL,
  prefix_len int(8) unsigned NOT NULL,
  prefix_bin varbinary(16) NOT NULL,
  prefix_bcast_bin varbinary(16) NOT NULL,
  ospf_route_type enum('Intra','Inter','Ext-1','Ext-2','NSSA-1','NSSA-2','') DEFAULT NULL,
  igp_flags varchar(20) NOT NULL,
  isIPv4 tinyint(4) NOT NULL,
  route_tag int(10) unsigned DEFAULT NULL,
  ext_route_tag bigint(20) unsigned DEFAULT NULL,
  metric int(10) unsigned NOT NULL,
  ospf_fwd_addr varchar(46) DEFAULT NULL,
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  sr_prefix_sids varchar(255) DEFAULT NULL,
  PRIMARY KEY (hash_id,peer_hash_id,local_node_hash_id),
  KEY idx_local_router_id (local_node_hash_id),
  KEY idx_path_attr_hash_id (path_attr_hash_id),
  KEY idx_range_prefix_bin (prefix_bcast_bin,prefix_bin)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
  PARTITION BY KEY (peer_hash_id);

--
-- Table structure for table path_attrs
--
DROP TABLE IF EXISTS path_attrs;
CREATE TABLE path_attrs (
  hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  origin varchar(16) NOT NULL,
  as_path varchar(8192) NOT NULL,
  as_path_count int(8) unsigned DEFAULT NULL,
  origin_as int(10) unsigned DEFAULT NULL,
  next_hop varchar(40) DEFAULT NULL,
  med int(10) unsigned DEFAULT NULL,
  local_pref int(10) unsigned DEFAULT NULL,
  aggregator varchar(64) DEFAULT NULL,
  community_list varchar(6000) DEFAULT NULL,
  ext_community_list varchar(2048) DEFAULT NULL,
  cluster_list varchar(2048) DEFAULT NULL,
  isAtomicAgg tinyint(4) DEFAULT 0,
  nexthop_isIPv4 tinyint(3) DEFAULT 1,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  originator_id varchar(15) DEFAULT NULL,
  PRIMARY KEY (hash_id,peer_hash_id),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_origin_as (origin_as),
  KEY idx_as_path_count (as_path_count)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC
  PARTITION BY KEY (peer_hash_id)
  PARTITIONS 48;

--
-- Table structure for table path_attr_log
--
DROP TABLE IF EXISTS path_attr_log;
CREATE TABLE path_attr_log (
  path_attr_hash_id char(32) NOT NULL,
  timestamp datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  peer_hash_id char(32) NOT NULL DEFAULT '',
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  prefix varchar(46) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL,
  labels varchar(255) DEFAULT NULL,
  path_id int(10) unsigned DEFAULT NULL,
  origin_as int(10) unsigned NOT NULL,
  PRIMARY KEY (id,peer_hash_id,timestamp),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_prefix_full (prefix,prefix_len),
  KEY idx_prefix (prefix),
  KEY idx_origin (origin_as)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
  PARTITION BY RANGE (to_days(timestamp))
  (
  PARTITION p2018_1_w1 VALUES LESS THAN (TO_DAYS('2018-01-08')) ENGINE = InnoDB,
  PARTITION p2018_1_w2 VALUES LESS THAN (TO_DAYS('2018-01-15')) ENGINE = InnoDB,
  PARTITION p2018_1_w3 VALUES LESS THAN (TO_DAYS('2018-01-23')) ENGINE = InnoDB,
  PARTITION p2018_1_w4 VALUES LESS THAN (TO_DAYS('2018-02-01')) ENGINE = InnoDB,
  PARTITION p2018_2_w1 VALUES LESS THAN (TO_DAYS('2018-02-08')) ENGINE = InnoDB,
  PARTITION p2018_2_w2 VALUES LESS THAN (TO_DAYS('2018-02-15')) ENGINE = InnoDB,
  PARTITION p2018_2_w3 VALUES LESS THAN (TO_DAYS('2018-02-23')) ENGINE = InnoDB,
  PARTITION p2018_2_w4 VALUES LESS THAN (TO_DAYS('2018-03-01')) ENGINE = InnoDB,
  PARTITION p2018_3_w1 VALUES LESS THAN (TO_DAYS('2018-03-08')) ENGINE = InnoDB,
  PARTITION p2018_3_w2 VALUES LESS THAN (TO_DAYS('2018-03-15')) ENGINE = InnoDB,
  PARTITION p2018_3_w3 VALUES LESS THAN (TO_DAYS('2018-03-23')) ENGINE = InnoDB,
  PARTITION p2018_3_w4 VALUES LESS THAN (TO_DAYS('2018-04-01')) ENGINE = InnoDB,
  PARTITION p2018_4_w1 VALUES LESS THAN (TO_DAYS('2018-04-08')) ENGINE = InnoDB,
  PARTITION p2018_4_w2 VALUES LESS THAN (TO_DAYS('2018-04-15')) ENGINE = InnoDB,
  PARTITION p2018_4_w3 VALUES LESS THAN (TO_DAYS('2018-04-23')) ENGINE = InnoDB,
  PARTITION p2018_4_w4 VALUES LESS THAN (TO_DAYS('2018-05-01')) ENGINE = InnoDB,
  PARTITION p2018_5_w1 VALUES LESS THAN (TO_DAYS('2018-05-08')) ENGINE = InnoDB,
  PARTITION p2018_5_w2 VALUES LESS THAN (TO_DAYS('2018-05-15')) ENGINE = InnoDB,
  PARTITION p2018_5_w3 VALUES LESS THAN (TO_DAYS('2018-05-23')) ENGINE = InnoDB,
  PARTITION p2018_5_w4 VALUES LESS THAN (TO_DAYS('2018-06-01')) ENGINE = InnoDB,
  PARTITION p2018_6_w1 VALUES LESS THAN (TO_DAYS('2018-06-08')) ENGINE = InnoDB,
  PARTITION p2018_6_w2 VALUES LESS THAN (TO_DAYS('2018-06-15')) ENGINE = InnoDB,
  PARTITION p2018_6_w3 VALUES LESS THAN (TO_DAYS('2018-06-23')) ENGINE = InnoDB,
  PARTITION p2018_6_w4 VALUES LESS THAN (TO_DAYS('2018-07-01')) ENGINE = InnoDB,
  PARTITION p2018_7_w1 VALUES LESS THAN (TO_DAYS('2018-07-08')) ENGINE = InnoDB,
  PARTITION p2018_7_w2 VALUES LESS THAN (TO_DAYS('2018-07-15')) ENGINE = InnoDB,
  PARTITION p2018_7_w3 VALUES LESS THAN (TO_DAYS('2018-07-23')) ENGINE = InnoDB,
  PARTITION p2018_7_w4 VALUES LESS THAN (TO_DAYS('2018-08-01')) ENGINE = InnoDB,
  PARTITION p2018_8_w1 VALUES LESS THAN (TO_DAYS('2018-08-08')) ENGINE = InnoDB,
  PARTITION p2018_8_w2 VALUES LESS THAN (TO_DAYS('2018-08-15')) ENGINE = InnoDB,
  PARTITION p2018_8_w3 VALUES LESS THAN (TO_DAYS('2018-08-23')) ENGINE = InnoDB,
  PARTITION p2018_8_w4 VALUES LESS THAN (TO_DAYS('2018-09-01')) ENGINE = InnoDB,
  PARTITION p2018_9_w1 VALUES LESS THAN (TO_DAYS('2018-09-08')) ENGINE = InnoDB,
  PARTITION p2018_9_w2 VALUES LESS THAN (TO_DAYS('2018-09-15')) ENGINE = InnoDB,
  PARTITION p2018_9_w3 VALUES LESS THAN (TO_DAYS('2018-09-23')) ENGINE = InnoDB,
  PARTITION p2018_9_w4 VALUES LESS THAN (TO_DAYS('2018-10-01')) ENGINE = InnoDB,
  PARTITION pOther VALUES LESS THAN MAXVALUE ENGINE = InnoDB);

--
-- Table structure for table peer_down_events
--
DROP TABLE IF EXISTS peer_down_events;
CREATE TABLE peer_down_events (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  peer_hash_id char(32) NOT NULL,
  bmp_reason tinyint(4) DEFAULT NULL,
  bgp_err_code int(10) unsigned DEFAULT NULL,
  bgp_err_subcode int(10) unsigned DEFAULT NULL,
  error_text varchar(255) DEFAULT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  PRIMARY KEY (id,peer_hash_id),
  KEY idx_error (peer_hash_id,bmp_reason),
  KEY idx_bgp_errors (bgp_err_code,bgp_err_subcode)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Table structure for table peer_up_events
--
DROP TABLE IF EXISTS peer_up_events;
CREATE TABLE peer_up_events (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  peer_hash_id char(32) NOT NULL,
  local_ip varchar(40) NOT NULL,
  local_bgp_id varchar(15) NOT NULL,
  local_port int(10) unsigned NOT NULL,
  local_hold_time int(10) unsigned NOT NULL,
  local_asn int(10) unsigned NOT NULL,
  remote_port int(10) unsigned NOT NULL,
  remote_hold_time int(10) unsigned NOT NULL,
  sent_capabilities varchar(4096) DEFAULT NULL,
  recv_capabilities varchar(4096) DEFAULT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  PRIMARY KEY (id,peer_hash_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

--
-- Table structure for table stat_reports
--
DROP TABLE IF EXISTS stat_reports;
CREATE TABLE stat_reports (
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  peer_hash_id char(32) NOT NULL,
  prefixes_rejected bigint(20) unsigned DEFAULT NULL,
  known_dup_prefixes bigint(20) unsigned DEFAULT NULL,
  known_dup_withdraws bigint(20) unsigned DEFAULT NULL,
  updates_invalid_by_cluster_list bigint(20) unsigned DEFAULT NULL,
  updates_invalid_by_as_path_loop bigint(20) unsigned DEFAULT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  updates_invalid_by_originagtor_id bigint(20) unsigned DEFAULT NULL,
  updates_invalid_by_as_confed_loop bigint(20) unsigned DEFAULT NULL,
  num_routes_adj_rib_in bigint(20) unsigned DEFAULT NULL,
  num_routes_local_rib bigint(20) unsigned DEFAULT NULL,
  PRIMARY KEY (id,peer_hash_id),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC;

--
-- Table structure for table rib
--
DROP TABLE IF EXISTS rib;
CREATE TABLE rib (
  hash_id char(32) NOT NULL,
  path_attr_hash_id char(32) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  isIPv4 tinyint(4) NOT NULL,
  origin_as int(10) unsigned NOT NULL,
  prefix varchar(40) NOT NULL,
  prefix_len int(10) unsigned NOT NULL,
  prefix_bin varbinary(16) NOT NULL,
  prefix_bcast_bin varbinary(16) NOT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
  first_added_timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  prefix_bits varchar(128) DEFAULT NULL,
  path_id int(10) unsigned DEFAULT NULL,
  labels varchar(255) DEFAULT NULL,
  isPrePolicy tinyint(4) NOT NULL DEFAULT 1,
  isAdjRibIn tinyint(4) NOT NULL DEFAULT 1,
  PRIMARY KEY (hash_id,peer_hash_id,isPrePolicy,isAdjRibIn),
  KEY idx_peer_id (peer_hash_id),
  KEY idx_path_id (path_attr_hash_id),
  KEY idx_prefix (prefix),
  KEY idx_prefix_len (prefix_len),
  KEY idx_prefix_bin (prefix_bin),
  KEY idx_addr_type (isIPv4),
  KEY idx_isWithdrawn (isWithdrawn),
  KEY idx_origin_as (origin_as),
  KEY idx_ts (timestamp),
  KEY idx_prefix_bits (prefix_bits),
  KEY idx_first_added_ts (first_added_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC
  PARTITION BY KEY (peer_hash_id)
  PARTITIONS 48;

DELIMITER ;;
CREATE TRIGGER rib_pre_update BEFORE UPDATE on rib
FOR EACH ROW
  BEGIN

    IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN

      IF (new.origin_as > 0 AND new.origin_as != 23456 AND old.isWithdrawn = True AND new.isWithdrawn = True) THEN
        INSERT IGNORE INTO gen_prefix_validation (prefix,prefix_len,recv_origin_as,rpki_origin_as,irr_origin_as,irr_source,prefix_bits,isIPv4)

          SELECT SQL_SMALL_RESULT new.prefix_bin,new.prefix_len,new.origin_as,
            rpki.origin_as, w.origin_as,w.source,new.prefix_bits,new.isIPv4
          FROM (SELECT new.prefix_bin as prefix_bin, new.prefix_len as prefix_len, new.origin_as as origin_as, new.prefix_bits,
                  new.isIPv4) rib
            LEFT JOIN gen_whois_route w ON (new.prefix_bin = w.prefix AND
                                            new.prefix_len = w.prefix_len)
            LEFT JOIN rpki_validator rpki ON (new.prefix_bin = rpki.prefix AND
                                              new.prefix_len >= rpki.prefix_len and new.prefix_len <= rpki.prefix_len_max)

        ON DUPLICATE KEY UPDATE rpki_origin_as = values(rpki_origin_as),
          irr_origin_as=values(irr_origin_as),irr_source=values(irr_source);
      END IF;



      IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
        IF (new.isWithdrawn = False) THEN
          IF (old.path_attr_hash_id != new.path_attr_hash_id AND old.path_attr_hash_id != '') THEN

            INSERT IGNORE INTO path_attr_log (prefix,prefix_len,labels,path_id,path_attr_hash_id,peer_hash_id,origin_as,timestamp)
            VALUES (old.prefix,old.prefix_len,old.labels,old.path_id,old.path_attr_hash_id,old.peer_hash_id,old.origin_as,
                    old.timestamp);

          END IF;


          IF (old.isWithdrawn = True AND old.timestamp < date_sub(new.timestamp, INTERVAL 6 HOUR)) THEN
            SET new.first_added_timestamp = current_timestamp(6);
          END IF;

        ELSE

          INSERT IGNORE INTO withdrawn_log
          (prefix,prefix_len,peer_hash_id,path_attr_hash_id,origin_as,timestamp)
          VALUES (old.prefix,old.prefix_len,old.peer_hash_id,
                  old.path_attr_hash_id,old.origin_as,new.timestamp);
        END IF;

      END IF;
    END IF;
  END ;;
DELIMITER ;


--
-- Table structure for table l3vpn_rib
--
DROP TABLE IF EXISTS l3vpn_rib;
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
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
  first_added_timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
  isWithdrawn bit(1) NOT NULL DEFAULT b'0',
  prefix_bits varchar(128) DEFAULT NULL,
  path_id int(10) unsigned DEFAULT NULL,
  labels varchar(255) DEFAULT NULL,
  isPrePolicy tinyint(4) NOT NULL DEFAULT 1,
  isAdjRibIn tinyint(4) NOT NULL DEFAULT 1,
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
  PARTITION BY KEY (peer_hash_id)
  PARTITIONS 48;

DELIMITER ;;
CREATE  TRIGGER l3vpn_rib_pre_update BEFORE UPDATE on l3vpn_rib
FOR EACH ROW
  BEGIN

    IF ( @TRIGGER_DISABLED is null OR @TRIGGER_DISABLED = FALSE ) THEN


      IF (new.hash_id = old.hash_id AND new.peer_hash_id = old.peer_hash_id) THEN
        IF (new.isWithdrawn = False) THEN
          IF (old.path_attr_hash_id != new.path_attr_hash_id AND old.path_attr_hash_id != '') THEN

            INSERT IGNORE INTO l3vpn_log (type,rd,prefix,prefix_len,path_attr_hash_id,peer_hash_id,timestamp)
            VALUES ('changed', old.rd, old.prefix,old.prefix_len,old.path_attr_hash_id,
                    old.peer_hash_id,old.timestamp);
          END IF;


          IF (old.isWithdrawn = True AND old.timestamp < date_sub(new.timestamp, INTERVAL 6 HOUR)) THEN
            SET new.first_added_timestamp = current_timestamp(6);
          END IF;

        ELSE

          INSERT IGNORE INTO l3vpn_log
          (type,rd,prefix,prefix_len,peer_hash_id,path_attr_hash_id,timestamp)
          VALUES ('withdrawn', old.rd, old.prefix,old.prefix_len,old.peer_hash_id,
                  old.path_attr_hash_id,new.timestamp);
        END IF;

      END IF;
    END IF;
  END ;;
DELIMITER ;

--
-- Table structure for table l3vpn_log
--
DROP TABLE IF EXISTS l3vpn_log;
CREATE TABLE l3vpn_log (
  peer_hash_id char(32) NOT NULL,
  type enum('withdrawn','changed') NOT NULL,
  prefix varchar(40) NOT NULL,
  rd varchar(30) NOT NULL,
  prefix_len int(10) unsigned NOT NULL,
  timestamp datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  path_attr_hash_id char(32) NOT NULL DEFAULT '',
  PRIMARY KEY (id,peer_hash_id,timestamp),
  KEY idx_prefix (prefix,prefix_len),
  KEY idx_rd (rd),
  KEY idx_type (type),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
  PARTITION BY RANGE  COLUMNS(timestamp)
  SUBPARTITION BY KEY (peer_hash_id)
  SUBPARTITIONS 32
  (
  PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
  PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
  PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
  PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
  PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
  PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
  PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
  PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
  PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
  PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
  PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);


--
-- Table structure for table as_path_analysis
--     Optionally enabled table to index AS paths
--
DROP TABLE IF EXISTS as_path_analysis;
CREATE TABLE as_path_analysis (
  asn int(10) unsigned NOT NULL,
  asn_left int(10) unsigned NOT NULL DEFAULT 0,
  asn_right int(10) unsigned NOT NULL DEFAULT 0,
  asn_left_is_peering tinyint DEFAULT 0,
  timestamp datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  PRIMARY KEY (asn,asn_left_is_peering,asn_left,asn_right),
  KEY idx_asn_left (asn_left),
  KEY idx_asn_right (asn_right),
  KEY idx_ts (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
  PARTITION BY KEY (asn)
  PARTITIONS 48;

--
-- Table structure for table gen_active_asns
--
DROP TABLE IF EXISTS gen_active_asns;
CREATE TABLE gen_active_asns (
  asn int(10) unsigned NOT NULL,
  old bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (asn)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table gen_asn_stats
--
DROP TABLE IF EXISTS gen_asn_stats;
CREATE TABLE gen_asn_stats (
  asn int(10) unsigned NOT NULL,
  isTransit tinyint(4) NOT NULL DEFAULT 0,
  isOrigin tinyint(4) NOT NULL DEFAULT 0,
  transit_v4_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  transit_v6_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  origin_v4_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  origin_v6_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  repeats bigint(20) unsigned NOT NULL DEFAULT 0,
  timestamp timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  transit_v4_change decimal(8,5) NOT NULL DEFAULT 0.00000,
  transit_v6_change decimal(8,5) NOT NULL DEFAULT 0.00000,
  origin_v4_change decimal(8,5) NOT NULL DEFAULT 0.00000,
  origin_v6_change decimal(8,5) NOT NULL DEFAULT 0.00000,
  PRIMARY KEY (asn,timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

DELIMITER ;;
CREATE TRIGGER ins_gen_asn_stats BEFORE INSERT ON gen_asn_stats
FOR EACH ROW
    BEGIN
        declare last_ts timestamp;
        declare v4_o_count bigint(20) unsigned default 0;
        declare v6_o_count bigint(20) unsigned default 0;
        declare v4_t_count bigint(20) unsigned default 0;
        declare v6_t_count bigint(20) unsigned default 0;
        SET sql_mode = '';
        SELECT transit_v4_prefixes,transit_v6_prefixes,origin_v4_prefixes,
                    origin_v6_prefixes,timestamp
            INTO v4_t_count,v6_t_count,v4_o_count,v6_o_count,last_ts
            FROM gen_asn_stats WHERE asn = new.asn 
            ORDER BY timestamp DESC limit 1;
        IF (new.transit_v4_prefixes = v4_t_count AND new.transit_v6_prefixes = v6_t_count
                AND new.origin_v4_prefixes = v4_o_count AND new.origin_v6_prefixes = v6_o_count) THEN
            set new.timestamp = last_ts;
        ELSE
    IF (v4_t_count > 0 AND new.transit_v4_prefixes > 0 AND new.transit_v4_prefixes != v4_t_count)  THEN
      SET new.transit_v4_change = cast(if(new.transit_v4_prefixes > v4_t_count,
                                   new.transit_v4_prefixes / v4_t_count,
                                   v4_t_count / new.transit_v4_prefixes * -1) as decimal(8,5));
    END IF;
    IF (v6_t_count > 0 AND new.transit_v6_prefixes > 0 AND new.transit_v6_prefixes != v6_t_count) THEN
      SET new.transit_v6_change = cast(if(new.transit_v6_prefixes > v6_t_count,
                                   new.transit_v6_prefixes / v6_t_count,
                                   v6_t_count / new.transit_v6_prefixes * -1) as decimal(8,5));
    END IF;
    IF (v4_o_count > 0 AND new.origin_v4_prefixes > 0 AND new.origin_v4_prefixes != v4_o_count) THEN
      SET new.origin_v4_change = cast(if(new.origin_v4_prefixes > v4_o_count,
                                   new.origin_v4_prefixes / v4_o_count,
                                   v4_o_count / new.origin_v4_prefixes * -1) as decimal(8,5));
    END IF;
    IF (v6_o_count > 0 AND new.origin_v6_prefixes > 0 AND new.origin_v6_prefixes != v6_o_count) THEN
      SET new.origin_v6_change = cast(if(new.origin_v6_prefixes > v6_o_count,
                                   new.origin_v6_prefixes / v6_o_count,
                                   v6_o_count / new.origin_v6_prefixes * -1) as decimal(8,5));
    END IF;
        END IF;
    END ;;
DELIMITER ;

--
-- Table structure for table gen_asn_stats_last
--
DROP TABLE IF EXISTS gen_asn_stats_last;
CREATE TABLE gen_asn_stats_last (
  asn int(10) unsigned NOT NULL,
  isTransit tinyint(4) NOT NULL DEFAULT 0,
  isOrigin tinyint(4) NOT NULL DEFAULT 0,
  transit_v4_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  transit_v6_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  origin_v4_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  origin_v6_prefixes bigint(20) unsigned NOT NULL DEFAULT 0,
  timestamp timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (asn)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table gen_l3vpn_chg_stats_bypeer
--
DROP TABLE IF EXISTS gen_l3vpn_chg_stats_bypeer;
CREATE TABLE gen_l3vpn_chg_stats_bypeer (
  interval_time datetime(6) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  updates int(10) unsigned NOT NULL DEFAULT 0,
  withdraws int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (interval_time,peer_hash_id),
  KEY idx_interval (interval_time),
  KEY idx_peer_hash_id (peer_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
  PARTITION BY RANGE  COLUMNS(interval_time)
  (
  PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
  PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
  PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
  PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
  PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
  PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
  PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
  PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
  PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
  PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
  PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);

DROP EVENT IF EXISTS chg_l3vpn_stats_bypeer;
DELIMITER ;;
CREATE EVENT chg_l3vpn_stats_bypeer ON SCHEDULE EVERY 5 MINUTE STARTS '2017-10-16 15:21:23' ON COMPLETION NOT PRESERVE ENABLE DO REPLACE INTO gen_l3vpn_chg_stats_bypeer (interval_time, peer_hash_id, updates,withdraws)

  SELECT c.IntervalTime,if (c.peer_hash_id is null, w.peer_hash_id, c.peer_hash_id) as peer_hash_id,
                        if (c.updates is null, 0, c.updates) as updates,
                        if (w.withdraws is null, 0, w.withdraws) as withdraws
  FROM
    (SELECT
                     from_unixtime(unix_timestamp(c.timestamp) - unix_timestamp(c.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, count(c.peer_hash_id) as updates
     FROM l3vpn_log c
     WHERE c.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 10 MINUTE), "%Y-%m-%d %H:%i:00")
           AND c.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
           AND type = 'changed'
     GROUP BY IntervalTime,c.peer_hash_id) c

    LEFT JOIN
    (SELECT
                     from_unixtime(unix_timestamp(w.timestamp) - unix_timestamp(w.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, count(w.peer_hash_id) as withdraws
     FROM l3vpn_log w
     WHERE w.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND w.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
           AND type = 'withdrawn'
     GROUP BY IntervalTime,w.peer_hash_id) w
      ON (c.IntervalTime = w.IntervalTime AND c.peer_hash_id = w.peer_hash_id);;
DELIMITER ;

--
-- Table structure for table gen_chg_stats_byasn
--
DROP TABLE IF EXISTS gen_chg_stats_byasn;
CREATE TABLE gen_chg_stats_byasn (
  interval_time datetime(6) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  origin_as int(10) unsigned NOT NULL,
  updates int(10) unsigned NOT NULL DEFAULT 0,
  withdraws int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (interval_time,peer_hash_id,origin_as),
  KEY idx_interval (interval_time),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_origin_as (origin_as)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
 PARTITION BY RANGE  COLUMNS(interval_time)
(
 PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
 PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
 PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
 PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
 PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
 PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);

DROP EVENT IF EXISTS chg_stats_byasn;
DELIMITER ;;
CREATE EVENT chg_stats_byasn ON SCHEDULE EVERY 5 MINUTE STARTS '2017-09-22 15:26:05' ON COMPLETION NOT PRESERVE ENABLE DO REPLACE INTO gen_chg_stats_byasn (interval_time, peer_hash_id,origin_as, updates,withdraws)
  SELECT c.IntervalTime,if (c.peer_hash_id is null, w.peer_hash_id, c.peer_hash_id) as peer_hash_id,
    if (c.origin_as is null, w.origin_as, c.origin_as),
                        if (c.updates is null, 0, c.updates) as updates,
                        if (w.withdraws is null, 0, w.withdraws) as withdraws
  FROM
    (SELECT
                                from_unixtime(unix_timestamp(c.timestamp) - unix_timestamp(c.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, origin_as, count(c.peer_hash_id) as updates
     FROM path_attr_log c
     WHERE c.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND c.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,c.peer_hash_id,origin_as) c

    LEFT JOIN
    (SELECT
                                from_unixtime(unix_timestamp(w.timestamp) - unix_timestamp(w.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, origin_as, count(w.peer_hash_id) as withdraws
     FROM withdrawn_log w
     WHERE w.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND w.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,w.peer_hash_id,origin_as) w
      ON (c.IntervalTime = w.IntervalTime AND c.peer_hash_id = w.peer_hash_id
          and c.origin_as = w.origin_as) ;;
DELIMITER ;


--
-- Table structure for table gen_chg_stats_bypeer
--
DROP TABLE IF EXISTS gen_chg_stats_bypeer;
CREATE TABLE gen_chg_stats_bypeer (
  interval_time datetime(6) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  updates int(10) unsigned NOT NULL DEFAULT 0,
  withdraws int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (interval_time,peer_hash_id),
  KEY idx_interval (interval_time),
  KEY idx_peer_hash_id (peer_hash_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1
 PARTITION BY RANGE  COLUMNS(interval_time)
(
 PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
 PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
 PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
 PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
 PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
 PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);

DROP EVENT IF EXISTS chg_stats_bypeer;
DELIMITER ;;
CREATE  EVENT chg_stats_bypeer ON SCHEDULE EVERY 5 MINUTE STARTS '2017-09-22 15:26:23' ON COMPLETION NOT PRESERVE ENABLE DO REPLACE INTO gen_chg_stats_bypeer (interval_time, peer_hash_id, updates,withdraws)
  SELECT c.IntervalTime,if (c.peer_hash_id is null, w.peer_hash_id, c.peer_hash_id) as peer_hash_id,
                        if (c.updates is null, 0, c.updates) as updates,
                        if (w.withdraws is null, 0, w.withdraws) as withdraws
  FROM
    (SELECT
                     from_unixtime(unix_timestamp(c.timestamp) - unix_timestamp(c.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, count(c.peer_hash_id) as updates
     FROM path_attr_log c
     WHERE c.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND c.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,c.peer_hash_id) c

    LEFT JOIN
    (SELECT
                     from_unixtime(unix_timestamp(w.timestamp) - unix_timestamp(w.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, count(w.peer_hash_id) as withdraws
     FROM withdrawn_log w
     WHERE w.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND w.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,w.peer_hash_id) w
      ON (c.IntervalTime = w.IntervalTime AND c.peer_hash_id = w.peer_hash_id)  ;;
DELIMITER ;


--
-- Table structure for table gen_chg_stats_byprefix
--
DROP TABLE IF EXISTS gen_chg_stats_byprefix;
CREATE TABLE gen_chg_stats_byprefix (
  interval_time datetime(6) NOT NULL,
  peer_hash_id char(32) NOT NULL,
  prefix varchar(46) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL,
  updates int(10) unsigned NOT NULL DEFAULT 0,
  withdraws int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (interval_time,peer_hash_id,prefix,prefix_len),
  KEY idx_interval (interval_time),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_prefix_full (prefix,prefix_len)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=4
 PARTITION BY RANGE  COLUMNS(interval_time)
(
 PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
 PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
 PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
 PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
 PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
 PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);

DROP EVENT IF EXISTS chg_stats_byprefix;
DELIMITER ;;
CREATE EVENT chg_stats_byprefix ON SCHEDULE EVERY 5 MINUTE STARTS '2017-09-22 15:26:13' ON COMPLETION NOT PRESERVE ENABLE DO REPLACE INTO gen_chg_stats_byprefix (interval_time, peer_hash_id, prefix, prefix_len, updates,withdraws)
  SELECT c.IntervalTime,if (c.peer_hash_id is null, w.peer_hash_id, c.peer_hash_id) as peer_hash_id,
                        if (c.prefix is null, w.prefix, c.prefix) as prefix,
                        if (c.prefix is null, w.prefix_len, c.prefix_len) as prefix_len,
                        if (c.updates is null, 0, c.updates) as updates,
                        if (w.withdraws is null, 0, w.withdraws) as withdraws
  FROM
    (SELECT
                                         from_unixtime(unix_timestamp(c.timestamp) - unix_timestamp(c.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, prefix, prefix_len, count(c.peer_hash_id) as updates
     FROM path_attr_log c
     WHERE c.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND c.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,c.peer_hash_id,prefix,prefix_len) c

    LEFT JOIN
    (SELECT
                                         from_unixtime(unix_timestamp(w.timestamp) - unix_timestamp(w.timestamp) % 60.0) AS IntervalTime,
       peer_hash_id, prefix, prefix_len, count(w.peer_hash_id) as withdraws
     FROM withdrawn_log w
     WHERE w.timestamp >= date_format(date_sub(current_timestamp, INTERVAL 25 MINUTE), "%Y-%m-%d %H:%i:00")
           AND w.timestamp <= date_format(current_timestamp, "%Y-%m-%d %H:%i:00")
     GROUP BY IntervalTime,w.peer_hash_id,prefix,prefix_len) w
      ON (c.IntervalTime = w.IntervalTime AND c.peer_hash_id = w.peer_hash_id
          AND c.prefix = w.prefix and c.prefix_len = w.prefix_len) ;;

DELIMITER ;

--
-- Table structure for table gen_prefix_validation
--
DROP TABLE IF EXISTS gen_prefix_validation;
CREATE TABLE gen_prefix_validation (
  prefix varbinary(16) NOT NULL,
  isIPv4 tinyint(4) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL DEFAULT 0,
  recv_origin_as int(10) unsigned NOT NULL,
  rpki_origin_as int(10) unsigned DEFAULT NULL,
  irr_origin_as int(10) unsigned DEFAULT NULL,
  irr_source varchar(32) DEFAULT NULL,
  timestamp timestamp NOT NULL DEFAULT current_timestamp(),
  prefix_bits varchar(128) NOT NULL,
  PRIMARY KEY (prefix,prefix_len,recv_origin_as),
  KEY idx_origin (recv_origin_as) USING HASH,
  KEY idx_prefix (prefix) USING BTREE,
  KEY idx_prefix_full (prefix,prefix_len) USING HASH,
  KEY idx_prefix_bits (prefix_bits) USING BTREE,
  KEY idx_ts (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table gen_whois_asn
--
DROP TABLE IF EXISTS gen_whois_asn;
CREATE TABLE gen_whois_asn (
  asn int(10) unsigned NOT NULL,
  as_name varchar(128) DEFAULT NULL,
  org_id varchar(64) DEFAULT NULL,
  org_name varchar(255) DEFAULT NULL,
  remarks text DEFAULT NULL,
  address varchar(255) DEFAULT NULL,
  city varchar(64) DEFAULT NULL,
  state_prov varchar(32) DEFAULT NULL,
  postal_code varchar(32) DEFAULT NULL,
  country varchar(24) DEFAULT NULL,
  raw_output text DEFAULT NULL,
  timestamp timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  source varchar(64) DEFAULT NULL,
  PRIMARY KEY (asn)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table gen_whois_route
--
DROP TABLE IF EXISTS gen_whois_route;
CREATE TABLE gen_whois_route (
  prefix varbinary(16) NOT NULL,
  prefix_len int(10) unsigned NOT NULL DEFAULT 0,
  descr blob DEFAULT NULL,
  origin_as int(11) NOT NULL,
  source varchar(32) NOT NULL,
  timestamp timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (prefix,prefix_len,origin_as),
  KEY idx_origin_as (origin_as)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table geo_ip
--
DROP TABLE IF EXISTS geo_ip;
CREATE TABLE geo_ip (
  addr_type enum('ipv4','ipv6') NOT NULL,
  ip_start varbinary(16) NOT NULL,
  ip_end varbinary(16) NOT NULL,
  country char(2) NOT NULL,
  stateprov varchar(80) NOT NULL,
  city varchar(80) NOT NULL,
  latitude float NOT NULL,
  longitude float NOT NULL,
  timezone_offset float NOT NULL,
  timezone_name varchar(64) NOT NULL,
  isp_name varchar(128) NOT NULL,
  connection_type enum('dialup','isdn','cable','dsl','fttx','wireless') DEFAULT NULL,
  organization_name varchar(128) DEFAULT NULL,
  PRIMARY KEY (ip_start),
  KEY idx_city (city),
  KEY idx_stateprov (stateprov),
  KEY idx_country (country),
  KEY idx_addr_type (addr_type),
  KEY idx_ip_end (ip_end),
  KEY idx_ip_range (ip_start,ip_end)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- v_geo_ip view
--
drop view IF EXISTS v_geo_ip;
create view v_geo_ip AS
  SELECT inet6_ntoa(ip_start) as ip_start,
         inet6_ntoa(ip_end) as ip_end,
    addr_type, country,stateprov,city,latitude,longitude,timezone_offset,timezone_name,
    isp_name,connection_type,organization_name,ip_start as ip_start_bin,ip_end as ip_end_bin
  FROM geo_ip;


--
-- Table structure for table geo_location
--
DROP TABLE IF EXISTS geo_location;
CREATE TABLE geo_location (
  country varchar(50) NOT NULL,
  city varchar(50) NOT NULL,
  latitude float NOT NULL,
  longitude float NOT NULL,
  PRIMARY KEY (country,city)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;



--
-- Table structure for table rpki_history_stats
--
DROP TABLE IF EXISTS rpki_history_stats;
CREATE TABLE rpki_history_stats (
  total_prefix int(10) unsigned NOT NULL,
  total_violations int(10) unsigned NOT NULL,
  timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (timestamp),
  KEY idx_timestamp (timestamp) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table rpki_validator
--
DROP TABLE IF EXISTS rpki_validator;
CREATE TABLE rpki_validator (
  prefix varbinary(16) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL DEFAULT 0,
  prefix_len_max tinyint(3) unsigned NOT NULL DEFAULT 0,
  origin_as int(10) unsigned NOT NULL,
  timestamp timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (prefix,prefix_len,prefix_len_max,origin_as),
  KEY idx_origin (origin_as),
  KEY idx_prefix (prefix),
  KEY idx_prefix_full (prefix,prefix_len)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table users
--
DROP TABLE IF EXISTS users;
CREATE TABLE users (
  username varchar(50) NOT NULL,
  password varchar(50) NOT NULL,
  type varchar(10) NOT NULL,
  PRIMARY KEY (username)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table withdrawn_log
--
DROP TABLE IF EXISTS withdrawn_log;
CREATE TABLE withdrawn_log (
  peer_hash_id char(32) NOT NULL,
  prefix varchar(40) NOT NULL,
  prefix_len int(10) unsigned NOT NULL,
  timestamp datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  path_attr_hash_id char(32) NOT NULL DEFAULT '',
  origin_as int(10) unsigned NOT NULL,
  PRIMARY KEY (id,peer_hash_id,timestamp),
  KEY idx_prefix (prefix,prefix_len),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_origin_as (origin_as)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
 PARTITION BY RANGE  COLUMNS(timestamp)
SUBPARTITION BY KEY (peer_hash_id)
SUBPARTITIONS 32
(PARTITION p2018_01 VALUES LESS THAN ('2018-02-01') ENGINE = InnoDB,
 PARTITION p2018_02 VALUES LESS THAN ('2018-03-01') ENGINE = InnoDB,
 PARTITION p2018_03 VALUES LESS THAN ('2018-04-01') ENGINE = InnoDB,
 PARTITION p2018_04 VALUES LESS THAN ('2018-05-01') ENGINE = InnoDB,
 PARTITION p2018_05 VALUES LESS THAN ('2018-06-01') ENGINE = InnoDB,
 PARTITION p2018_06 VALUES LESS THAN ('2018-07-01') ENGINE = InnoDB,
 PARTITION p2018_07 VALUES LESS THAN ('2018-08-01') ENGINE = InnoDB,
 PARTITION p2018_08 VALUES LESS THAN ('2018-09-01') ENGINE = InnoDB,
 PARTITION p2018_09 VALUES LESS THAN ('2018-10-01') ENGINE = InnoDB,
 PARTITION p2018_10 VALUES LESS THAN ('2018-11-01') ENGINE = InnoDB,
 PARTITION pOther VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB);

--
-- VIEWS
--
drop view IF EXISTS v_peer_prefix_report_last_id;
create view v_peer_prefix_report_last_id AS
SELECT max(id) as id,peer_hash_id
          FROM stat_reports
          WHERE timestamp >= date_sub(current_timestamp, interval 72 hour)
          GROUP BY peer_hash_id;

drop view IF EXISTS v_peer_prefix_report_last;
create view v_peer_prefix_report_last AS
SELECT if (length(r.name) > 0, r.name, r.ip_address) as RouterName, if (length(p.name) > 0, p.name, p.peer_addr) as PeerName,
                     s.timestamp as TS, prefixes_rejected as Rejected,
                     updates_invalid_by_as_confed_loop AS ConfedLoop, updates_invalid_by_as_path_loop AS ASLoop,
                     updates_invalid_by_cluster_list AS InvalidClusterList, updates_invalid_by_originagtor_id AS InvalidOriginator,
                     known_dup_prefixes AS  KnownPrefix_DUP, known_dup_withdraws AS KnownWithdraw_DUP,
                     num_routes_adj_rib_in as Pre_RIB,num_routes_local_rib as Post_RIB,
                     r.hash_id as router_hash_id, p.hash_id as peer_hash_id

          FROM v_peer_prefix_report_last_id i
                        STRAIGHT_JOIN stat_reports s on (i.id = s.id)
                        STRAIGHT_JOIN bgp_peers p on (s.peer_hash_id = p.hash_id)
                        STRAIGHT_JOIN routers r on (p.router_hash_id = r.hash_id)
          GROUP BY s.peer_hash_id;

drop view IF EXISTS v_peer_prefix_report;
create view v_peer_prefix_report AS
SELECT if (length(r.name) > 0, r.name, r.ip_address) as RouterName, if (length(p.name) > 0, p.name, p.peer_addr) as PeerName,
                     s.timestamp as TS, prefixes_rejected as Rejected,
                     updates_invalid_by_as_confed_loop AS ConfedLoop, updates_invalid_by_as_path_loop AS ASLoop,
                     updates_invalid_by_cluster_list AS InvalidClusterList, updates_invalid_by_originagtor_id AS InvalidOriginator,
                     known_dup_prefixes AS  KnownPrefix_DUP, known_dup_withdraws AS KnownWithdraw_DUP,
                     num_routes_adj_rib_in as Pre_RIB,num_routes_local_rib as Post_RIB,
                     r.hash_id as router_hash_id, p.hash_id as peer_hash_id

          FROM stat_reports s  JOIN  bgp_peers p on (s.peer_hash_id = p.hash_id) join routers r on (p.router_hash_id = r.hash_id)
          order  by s.timestamp desc;

drop view IF EXISTS v_routes;
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
                r.prefix_bits,r.isPrePolicy,r.isAdjRibIn
        FROM bgp_peers p JOIN rib r ON (r.peer_hash_id = p.hash_id)
            JOIN path_attrs path ON (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
            JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
       WHERE r.isWithdrawn = False;

drop view IF EXISTS v_all_routes;
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


drop view IF EXISTS v_routes_history;
CREATE VIEW v_routes_history AS
  SELECT
                rtr.name as RouterName, rtr.ip_address as RouterAddress,
	        p.name AS PeerName,
                pathlog.prefix AS Prefix,pathlog.prefix_len AS PrefixLen,pathlog.labels AS Labels,pathlod.path_id AS path_id,
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


drop view IF EXISTS v_routes_withdraws;
CREATE VIEW v_routes_withdraws AS
SELECT  rtr.name as RouterName, rtr.ip_address as RouterAddress,
	p.name AS PeerName,
        log.prefix AS Prefix,log.prefix_len AS PrefixLen,
        path.origin AS Origin,path.origin_as AS Origin_AS,path.med AS MED,path.local_pref AS LocalPref,
        path.next_hop AS NH,path.as_path AS AS_Path,path.as_path_count AS ASPath_Count,
        path.community_list AS Communities,path.ext_community_list AS ExtCommunities,path.cluster_list AS ClusterList,
        path.aggregator AS Aggregator,p.peer_addr AS PeerAddress,p.peer_as AS PeerASN,
        p.isIPv4 AS isPeerIPv4,p.isL3VPNpeer AS isPeerVPN,log.id AS id,log.timestamp AS LastModified,
        log.path_attr_hash_id AS path_attr_hash_id,log.peer_hash_id AS peer_hash_id,rtr.hash_id AS router_hash_id
    FROM withdrawn_log log
         STRAIGHT_JOIN path_attrs path ON (path.hash_id = log.path_attr_hash_id and path.peer_hash_id = log.peer_hash_id)
         STRAIGHT_JOIN bgp_peers p ON (log.peer_hash_id = p.hash_id)
         LEFT JOIN routers rtr ON (p.router_hash_id = rtr.hash_id)
    ORDER BY log.timestamp desc;

drop view IF EXISTS v_peers;
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
                                         LEFT JOIN gen_whois_asn w ON (p.peer_as = w.asn);

--
-- L3VPN views
--
drop view IF EXISTS v_l3vpn_routes;
CREATE VIEW v_l3vpn_routes AS
	select if((length(rtr.name) > 0),rtr.name,rtr.ip_address) AS RouterName,
	if((length(p.name) > 0),p.name,p.peer_addr) AS PeerName,
 	r.rd AS RD,r.prefix AS Prefix,r.prefix_len AS PrefixLen,path.origin AS Origin,
 	r.origin_as AS Origin_AS,path.med AS MED,path.local_pref AS LocalPref,
 	path.next_hop AS NH,path.as_path AS AS_Path,
	path.as_path_count AS ASPath_Count,path.community_list AS Communities,
	path.ext_community_list AS ExtCommunities,path.cluster_list AS ClusterList,
	path.aggregator AS Aggregator,p.peer_addr AS PeerAddress,p.peer_as AS PeerASN,
	r.isIPv4 AS isIPv4,p.isIPv4 AS isPeerIPv4,p.isL3VPNpeer AS isPeerVPN,
	r.timestamp AS LastModified,r.first_added_timestamp AS FirstAddedTimestamp,
	r.prefix_bin AS prefix_bin,r.path_id AS path_id,r.labels AS labels,r.hash_id AS rib_hash_id,
	r.path_attr_hash_id AS path_hash_id,r.peer_hash_id AS peer_hash_id,
	rtr.hash_id AS router_hash_id,r.isWithdrawn AS isWithdrawn,
	r.prefix_bits AS prefix_bits,r.isPrePolicy AS isPrePolicy,r.isAdjRibIn AS isAdjRibIn
     from bgp_peers p
               join l3vpn_rib r on (r.peer_hash_id = p.hash_id)
	    join path_attrs path on (path.hash_id = r.path_attr_hash_id and path.peer_hash_id = r.peer_hash_id)
              join routers rtr on (p.router_hash_id = rtr.hash_id)
      where  r.isWithdrawn = 0;

--
-- Link State views
--
drop view IF EXISTS v_ls_nodes;
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
	    JOIN ls_links links ON (ls_nodes.hash_id = links.local_node_hash_id and links.isWithdrawn = False)
            JOIN bgp_peers p on (p.hash_id = ls_nodes.peer_hash_id) JOIN
                             routers r on (p.router_hash_id = r.hash_id)
         WHERE not ls_nodes.igp_router_id regexp "\..[1-9A-F]00$" AND ls_nodes.igp_router_id not like "%]" and ls_nodes.iswithdrawn = False
	GROUP BY ls_nodes.peer_hash_id,ls_nodes.hash_id,links.mt_id;


drop view IF EXISTS v_ls_links;
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
            AND ln.peer_hash_id = localn.peer_hash_id and localn.iswithdrawn = False)
         JOIN ls_nodes remoten ON (ln.remote_node_hash_id = remoten.hash_id
            AND ln.peer_hash_id = remoten.peer_hash_id and remoten.iswithdrawn = False)
	WHERE ln.isWithdrawn = False;


drop view IF EXISTS v_ls_links_new;
CREATE VIEW v_ls_links_new AS
SELECT localn.name as Local_Router_Name,remoten.name as Remote_Router_Name,
         localn.igp_router_id as Local_IGP_RouterId,localn.router_id as Local_RouterId,
         remoten.igp_router_id Remote_IGP_RouterId, remoten.router_id as Remote_RouterId,
         localn.bgp_ls_id as bgpls_id,
         IF (ln.protocol in ('OSPFv2', 'OSPFv3'),localn.ospf_area_id, localn.isis_area_id) as AreaId,
      ln.mt_id as MT_ID,interface_addr as InterfaceIP,neighbor_addr as NeighborIP,
      ln.isIPv4,ln.protocol,igp_metric,local_link_id,remote_link_id,admin_group,max_link_bw,max_resv_bw,
      unreserved_bw,te_def_metric,mpls_proto_mask,srlg,ln.name,ln.timestamp,local_node_hash_id,remote_node_hash_id,
      localn.igp_router_id as localn_igp_router_id_bin,remoten.igp_router_id as remoten_igp_router_id_bin,
      ln.path_attr_hash_id as path_attr_hash_id, ln.peer_hash_id as peer_hash_id,
      if(ln.iswithdrawn, 'INACTIVE', 'ACTIVE') as state
  FROM ls_links ln JOIN ls_nodes localn ON (ln.local_node_hash_id = localn.hash_id
            AND ln.peer_hash_id = localn.peer_hash_id and localn.iswithdrawn = False)
         JOIN ls_nodes remoten ON (ln.remote_node_hash_id = remoten.hash_id
            AND ln.peer_hash_id = remoten.peer_hash_id and remoten.iswithdrawn = False);


drop view IF EXISTS v_ls_prefixes;
CREATE VIEW v_ls_prefixes AS
SELECT localn.name as Local_Router_Name,localn.igp_router_id as Local_IGP_RouterId,
         localn.router_id as Local_RouterId,
         lp.id,lp.mt_id,prefix as Prefix, prefix_len,ospf_route_type,metric,lp.protocol,
         lp.timestamp,lp.prefix_bcast_bin,lp.prefix_bin,
         lp.peer_hash_id
    FROM ls_prefixes lp JOIN ls_nodes localn ON (lp.local_node_hash_id = localn.hash_id)
    WHERE lp.isWithdrawn = False;


--
-- END
--
