# OpenBMP MySQL Consumer


The MySQL consumer implements the OpenBMP [Message Bus API](http://openbmp.org/#!docs/MESSAGE_BUS_API.md) **parsed** messages to collect and store BMP/BGP data of all collectors, routers, and peers in real-time. The consumer provides the same data storage that OpenBMP collector versions 0.10.x and less implemented.

> #### NOTE
> This is currently using the same method to consume messages as the console app. This will change to use the new **org.apache.kafka.clients.consumer** when Kafka 0.8.3 is released .  The change will enable better handling of offsets and will not require zookeeper. 
 

Implementation
--------------
The consumer implements all the **parsed message bus API's** to store all data.  The data is stored for real-time and historical reporting. 

### Consumer Resume/Restart
Apache Kafka implements a rotating log approach for the message bus stream.  The default retention is large enough to handle hundreds of peers with full internet routing tables for hours.  The consumer tracks where it is within the log/stream by using offsets (via Apache Kafka API's).  This enables the consumer to resume where it left off in the event the consumer is restarted.    

The consumer offset is tracked by the client id/group id.  You can shutdown one consumer on host A and then start a new consumer on host B using the same id's to enable host B to resume where host A left off.  In other words, the consumer is portable since it does not maintain state locally. 

> #### Important
> Try to keep the mysql consumer running at all times. While it is supported to stop/start the consumer without having to touch the collector, long durations of downtime can result in the consumer taking a while to catch-up.  
> 
> When the MySQL connection is running slow or when the consumer is catching up, you will see an INFO log message that reports that the queue size is above 500.  This will repeat every 10 seconds if it stays above 500.  Lack of these messages indicates that the consumer is running real-time. 

### Threading and MySQL Connections
The current thread model is one thread per topic. Each thread has it's own database connection.   This enables higher throughput/transactions with the database since each can consume a vCPU/core on the database side. 

#### Load balancing
Additional threads can be created for each partition to load balance within the same consumer over multiple CPU's.   Alternatively, multiple consumers can be run.   Peer messages are always within the same partition so that the consumer that is handling the partition will be able to maintain order.   The collector uses the peer hash as the key, which is used for partitioning. 

### RIB Dump Handling
Per BMP draft Section 3.3, when BMP monitoring station connection is established the router begins to forward all Adj-RIB-In (either pre or post policy) data to the monitoring station.  An End-Of-RIB is sent to indicate that the initial table dump is complete.   Since the MySQL consumer can catch-up/restart where it left off, the collector does not need to restart the BMP feed, nor is it required to refresh the peer(s).

Building Source
---------------

### Dependancies
You will need Java 1.7 or greater as well as maven 3.x or greater.

#### Build
You can build from source using maven as below:

    git clone https://github.com/OpenBMP/openbmp-mysql-consumer.git
    cd openbmp-mysql-consumer
    mvn -DskipTests=True clean package
    
The above will create a JAR file under **target/**.  The JAR file is the complete package, which includes the dependancies. 

Running
-------
Make sure to define the memory as below, otherwise Java likes to use more than is needed.
    
    nohup java -Xmx512M -Xms512M -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC \
        -jar openbmp-mysql-consumer-0.1.0-082815.jar  -dh db.openbmp.org \
        -dn openBMP -du openbmp -dp openbmpNow -zk localhost > mysql-consumer.log &     

### Debug/Logging Changes
You can define your own **log4j2.yml** (yml or any format you prefer) by suppling the ```-Dlog4j.configurationFile=<filename>``` option to java when running the JAR.   

#### Example log4j2.yml 
The below is an example log4j2 configuration for debug logging to a rolling file.

```
Configuration:
  status: warn

  Appenders:
    Console:
      name: Console
      target: SYSTEM_OUT
      PatternLayout:
        Pattern: "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n"

    RollingFile:
      name: file
      fileName: "openbmp-mysql.log"
      filePattern: "$${date:yyyy-MM}/app-%d{MM-dd-yyyy}-%i.log.gz"
      PatternLayout:
        Pattern: "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n"
      Policies:
        SizeBasedTriggeringPolicy:
          size: "75 MB"
      DefaultRolloverStrategy:
        max: 30

  Loggers:
    Root:
      level: debug
      AppenderRef:
        ref: file

```



TODO
-------------------------

* Add command line option to adjust the offset to tail of stream.  This is to bypass catching up on startup
* See if we can expose the tail offset value and the current offset value that the consumer is reading.  This can allow the ability to report via log progress on catching up
* Add support for multiple partitions/load balancing



