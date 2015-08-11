DB Alter Changes for 0.11.0 from 0.9.0
--------------------------------------
This is the first release with Kafka integration and therefore it is recommended to start with a fresh database.  

### Preserve your old database
Only issue the below if you want to preserve your old data.


```
mysql -u root --password=<root password> <<DB_CREATE

   RENAME DATABASE openBMP openBMPold;
   CREATE DATABASE openBMP;
   grant all on openBMP.* to 'openbmp'@'localhost';
   grant all on openBMP.* to 'openbmp'@'%';
DB_CREATE
```

### Import the latest schema

> #### Note
> This will replace the current if it exists

    curl -o mysql-openbmp-current.db https://raw.githubusercontent.com/OpenBMP/openbmp-mysql-consumer/master/database/mysql-openbmp-current.db
    mysql -u root -p openBMP < mysql-openbmp-current.db

