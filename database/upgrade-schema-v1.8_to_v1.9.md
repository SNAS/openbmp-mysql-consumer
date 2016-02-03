DB Alter Changes for v1.8 to v1.9
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

      CREATE TABLE geo_locatio` (
        country char(2) NOT NULL,
        city varchar(50) NOT NULL,
        latitude float DEFAULT NULL,
        longitude float DEFAULT NULL,
        PRIMARY KEY (country,city)
      ) ENGINE=InnoDB

UPGRADE
```
