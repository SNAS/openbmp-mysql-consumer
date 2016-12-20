/*
 * Copyright (c) 2015-2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.openbmp;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * MySQL OpenBMP consumer
 *      Consumes the openbmp.parsed.* topic streams and stores the data into MySQL.
 */
public class MySQLConsumerApp
{
    private static final Logger logger = LogManager.getFormatterLogger(MySQLConsumerApp.class.getName());
    private ExecutorService executor;
    private final Config cfg;
    private List<MySQLConsumerRunnable> consumerThreads;


    /**
     * routerConMap is a persistent map of collectors and routers. It's a hash of hashes.
     *      routerConMap[collectorHashId][RouterIp] = reference count of connections
     */
    private Map<String,Map<String, Integer>> routerConMap;

    /**
     *
     * @param cfg       Configuration - e.g. DB credentials
     */
    public MySQLConsumerApp(Config cfg) {

        this.cfg = cfg;
        consumerThreads = new ArrayList<>();
        routerConMap = new ConcurrentHashMap<String, Map<String, Integer>>();

    }

    public void shutdown() {
        logger.debug("Shutting down MySQL consumer app");

        for (MySQLConsumerRunnable thr: consumerThreads) {
            thr.shutdown();
        }

        if (executor != null) executor.shutdown();
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                logger.warn("Timed out waiting for consumer threads to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted during shutdown, exiting uncleanly");
        }
    }

    public void run() {
        List<String> topics = new ArrayList<String>();

        topics.add("openbmp.parsed.collector");
        topics.add("openbmp.parsed.router");
        topics.add("openbmp.parsed.peer");
        topics.add("openbmp.parsed.bmp_stat");
        topics.add("openbmp.parsed.ls_node");
        topics.add("openbmp.parsed.ls_link");
        topics.add("openbmp.parsed.ls_prefix");
        topics.add("openbmp.parsed.base_attribute");
        topics.add("openbmp.parsed.unicast_prefix");

        int numConsumerThreads = 1;
        executor = Executors.newFixedThreadPool(numConsumerThreads);

        for (int i=0; i < numConsumerThreads; i++) {
            MySQLConsumerRunnable consumer = new MySQLConsumerRunnable(createConsumerConfig(cfg), topics, cfg, routerConMap);
            executor.submit(consumer);
            consumerThreads.add(consumer);
        }


    }

    private static Properties createConsumerConfig(Config cfg) {
        Properties props = new Properties();
        props.put("bootstrap.servers", cfg.getBootstrapServer());
        props.put("group.id", cfg.getGroupId());
        props.put("client.id", cfg.getClientId() != null ? cfg.getClientId() : cfg.getGroupId());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("session.timeout.ms", "16000");
        props.put("max.partition.fetch.bytes", "2000000");
        props.put("heartbeat.interval.ms", "10000");
        props.put("max.poll.records", "2000");

        props.put("enable.auto.commit", "true");

        if (cfg.getOffsetLargest() == Boolean.FALSE) {
            logger.info("Using smallest for Kafka offset reset");
            props.put("auto.offset.reset", "earliest");
        } else {
            logger.info("Using largest for Kafka offset reset");
            props.put("auto.offset.reset", "latest");
        }

        return props;
    }


    public static void main(String[] args) {
        Config cfg = Config.getInstance();

        cfg.parse(args);

        // Validate DB connection
        try {
            Connection con = DriverManager.getConnection(
                    "jdbc:mariadb://" + cfg.getDbHost() + "/" + cfg.getDbName() +
                            "?tcpKeepAlive=1&socketTimeout=1000&useCompression=true&autoReconnect=true&allowMultiQueries=true",
                    cfg.getDbUser(), cfg.getDbPw());
            con.close();

        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(2);
        }

        MySQLConsumerApp mysqlApp = new MySQLConsumerApp(cfg);

        mysqlApp.run();

        try {
            while (true) {
                if (cfg.getStatsInterval() > 0) {
                    Thread.sleep(cfg.getStatsInterval() * 1000);

                    for (int i = 0; i < mysqlApp.consumerThreads.size(); i++ ) {
                        logger.info("-- STATS --   thread: %d  read: %-10d  queue: %-10d",
                                    i, mysqlApp.consumerThreads.get(i).getMessageCount(),
                                    mysqlApp.consumerThreads.get(i).getQueueSize());
                        logger.info("           collector messages: %d",
                                mysqlApp.consumerThreads.get(i).getCollector_msg_count());
                        logger.info("              router messages: %d",
                                mysqlApp.consumerThreads.get(i).getRouter_msg_count());
                        logger.info("                peer messages: %d",
                                mysqlApp.consumerThreads.get(i).getPeer_msg_count());
                        logger.info("             reports messages: %d",
                                mysqlApp.consumerThreads.get(i).getStat_msg_count());
                        logger.info("      base attribute messages: %d",
                                mysqlApp.consumerThreads.get(i).getBase_attribute_msg_count());
                        logger.info("      unicast prefix messages: %d",
                                mysqlApp.consumerThreads.get(i).getUnicast_prefix_msg_count());
                        logger.info("             LS node messages: %d",
                                mysqlApp.consumerThreads.get(i).getLs_node_msg_count());
                        logger.info("             LS link messages: %d",
                                mysqlApp.consumerThreads.get(i).getLs_link_msg_count());
                        logger.info("           LS prefix messages: %d",
                                mysqlApp.consumerThreads.get(i).getLs_prefix_msg_count());
                    }

                } else {
                    Thread.sleep(15000);
                }
            }
        } catch (InterruptedException ie) {

        }

        mysqlApp.shutdown();
    }
}
