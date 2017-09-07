## Partition path\_attr\_log by week

In some cases, it's ideal to prune data sooner than a month.  This requires the data to be partitioned by week instead of month.   

### Partition by Week
Issue the following to drop and create the path\_attr\_log table using weekly partitioning. 

```sql
 DROP TABLE IF EXISTS path_attr_log;
 CREATE TABLE path_attr_log (
  path_attr_hash_id char(32) NOT NULL,
  timestamp datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  peer_hash_id char(32) NOT NULL DEFAULT '',
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  prefix varchar(46) NOT NULL,
  prefix_len tinyint(3) unsigned NOT NULL,
  origin_as int(10) unsigned NOT NULL,
  PRIMARY KEY (id,peer_hash_id,timestamp),
  KEY idx_ts (timestamp),
  KEY idx_peer_hash_id (peer_hash_id),
  KEY idx_prefix_full (prefix,prefix_len),
  KEY idx_prefix (prefix),
  KEY idx_origin (origin_as)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
 PARTITION BY RANGE (to_days(timestamp))
(PARTITION p2017_08_w4 VALUES LESS THAN (to_days('2017-09-01')),
 PARTITION p2017_09_w1 VALUES LESS THAN (to_days('2017-09-08')),
 PARTITION p2017_09_w2 VALUES LESS THAN (to_days('2017-09-15')),
 PARTITION p2017_09_w3 VALUES LESS THAN (to_days('2017-09-25')),
 PARTITION p2017_09_w4 VALUES LESS THAN (to_days('2017-10-01')),
 PARTITION p2017_10_w1 VALUES LESS THAN (to_days('2017-10-08')),
 PARTITION p2017_10_w2 VALUES LESS THAN (to_days('2017-10-15')),
 PARTITION p2017_10_w3 VALUES LESS THAN (to_days('2017-10-25')),
 PARTITION p2017_10_w4 VALUES LESS THAN (to_days('2017-11-01')),
 PARTITION p2017_11_w1 VALUES LESS THAN (to_days('2017-11-08')),
 PARTITION p2017_11_w2 VALUES LESS THAN (to_days('2017-11-15')),
 PARTITION p2017_11_w3 VALUES LESS THAN (to_days('2017-11-25')),
 PARTITION p2017_11_w4 VALUES LESS THAN (to_days('2017-12-01')),
 PARTITION p2017_12_w1 VALUES LESS THAN (to_days('2017-12-08')),
 PARTITION p2017_12_w2 VALUES LESS THAN (to_days('2017-12-15')),
 PARTITION p2017_12_w3 VALUES LESS THAN (to_days('2017-12-25')),
 PARTITION p2017_12_w4 VALUES LESS THAN (to_days('2018-01-01')),
 PARTITION p2018_01_w1 VALUES LESS THAN (to_days('2018-01-08')),
 PARTITION p2018_01_w2 VALUES LESS THAN (to_days('2018-01-15')),
 PARTITION p2018_01_w3 VALUES LESS THAN (to_days('2018-01-25')),
 PARTITION p2018_01_w4 VALUES LESS THAN (to_days('2018-02-01')),
 PARTITION p2018_02_w1 VALUES LESS THAN (to_days('2018-02-08')),
 PARTITION p2018_02_w2 VALUES LESS THAN (to_days('2018-02-15')),
 PARTITION p2018_02_w3 VALUES LESS THAN (to_days('2018-02-25')),
 PARTITION p2018_02_w4 VALUES LESS THAN (to_days('2018-03-01')),
 PARTITION p2018_03_w1 VALUES LESS THAN (to_days('2018-03-08')),
 PARTITION p2018_03_w2 VALUES LESS THAN (to_days('2018-03-15')),
 PARTITION p2018_03_w3 VALUES LESS THAN (to_days('2018-03-25')),
 PARTITION p2018_03_w4 VALUES LESS THAN (to_days('2018-04-01')),
 PARTITION p2018_04_w1 VALUES LESS THAN (to_days('2018-04-08')),
 PARTITION p2018_04_w2 VALUES LESS THAN (to_days('2018-04-15')),
 PARTITION p2018_04_w3 VALUES LESS THAN (to_days('2018-04-25')),
 PARTITION p2018_04_w4 VALUES LESS THAN (to_days('2018-05-01')),
 PARTITION p2018_05_w1 VALUES LESS THAN (to_days('2018-05-08')),
 PARTITION p2018_05_w2 VALUES LESS THAN (to_days('2018-05-15')),
 PARTITION p2018_05_w3 VALUES LESS THAN (to_days('2018-05-25')),
 PARTITION p2018_05_w4 VALUES LESS THAN (to_days('2018-06-01')),
 PARTITION p2018_06_w1 VALUES LESS THAN (to_days('2018-06-08')),
 PARTITION p2018_06_w2 VALUES LESS THAN (to_days('2018-06-15')),
 PARTITION p2018_06_w3 VALUES LESS THAN (to_days('2018-06-25')),
 PARTITION p2018_06_w4 VALUES LESS THAN (to_days('2018-07-01')),
 PARTITION pOther VALUES LESS THAN MAXVALUE);
```

### Prune old data
Prune the past weeks using the following:

```sql
alter table path_attr_log drop partition p2017_08_w4;
```

### Repartition is needed over time

The partitions above only go up to the last week of June, 2018.  You will need to repartition using the following:

```sql
alter table path_attr_log  REORGANIZE PARTITION pOther into (
 PARTITION p2018_07_w1 VALUES LESS THAN (to_days('2018-07-08')),
 PARTITION p2018_07_w2 VALUES LESS THAN (to_days('2018-07-15')),
 PARTITION p2018_07_w3 VALUES LESS THAN (to_days('2018-07-25')),
 PARTITION p2018_07_w4 VALUES LESS THAN (to_days('2018-08-01')),
 PARTITION pOther VALUES LESS THAN (MAXVALUE));
```
