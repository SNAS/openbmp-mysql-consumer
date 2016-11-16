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

UPGRADE
```
