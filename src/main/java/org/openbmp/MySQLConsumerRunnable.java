/*
 * Copyright (c) 2015-2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.openbmp;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.openbmp.api.parsed.message.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbmp.mysqlquery.*;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/**
 * MySQL Consumer class
 *
 *   A thread to process a topic partition.  Supports all openbmp.parsed.* topics.
 */
public class MySQLConsumerRunnable implements Runnable {
    private final Integer SUBSCRIBE_INTERVAL_MILLI = 10000;  // topic subscription interval
    private final Integer NUM_WRITER_THREADS = 3;            // The number of writers to run

    private Boolean running;

    private ExecutorService executor;
    private Long last_collector_msg_time;


    private KafkaConsumer<String, String> consumer;
    private ConsumerRebalanceListener rebalanceListener;
    private Properties props;
    private List<String> topics;
    private Config cfg;
    private Map<String,Map<String, Integer>> routerConMap;

    private int topics_subscribed_count;
    private boolean topics_all_subscribed;

    private BigInteger messageCount;
    private long collector_msg_count;
    private long router_msg_count;
    private long peer_msg_count;
    private long base_attribute_msg_count;
    private long unicast_prefix_msg_count;
    private long l3vpn_prefix_msg_count;
    private long ls_node_msg_count;
    private long ls_link_msg_count;
    private long ls_prefix_msg_count;
    private long stat_msg_count;

    private Collection<TopicPartition> pausedTopics;
    private long last_paused_time;

    /*
     * Each writer object has the following:
     *      Connection to MySQL
     *      FIFO msg queue
     *      Map of assigned record keys to the writer
     */
    List<MySQLWriterObject> writers;

    private static final Logger logger = LogManager.getFormatterLogger(MySQLConsumerRunnable.class.getName());

    /**
     * Constructor
     *
     * @param props                Kafka properties/configuration
     * @param topics               Topics to subscribe to
     * @param cfg                  Configuration from cli/config file
     * @param routerConMap         Persistent router state tracking
     */
    public MySQLConsumerRunnable(Properties props, List<String> topics, Config cfg,
                                 Map<String,Map<String, Integer>> routerConMap) {


        messageCount = BigInteger.valueOf(0);
        this.topics = topics;
        this.props = props;
        this.cfg = cfg;
        this.routerConMap = routerConMap;

        this.running = false;

        pausedTopics = new HashSet<>();
        last_paused_time = 0L;

        /*
         * It's imperative to first process messages from some topics before subscribing to others.
         *    When connecting to Kafka, topics will be subscribed at an interval.  When the
         *    topics_subscribe_count is equal to the topics size, then all topics have been subscribed to.
         */
        this.topics_subscribed_count = 0;
        this.topics_all_subscribed = false;

        this.rebalanceListener = new ConsumerRebalanceListener(consumer);

        /*
         * Start MySQL Writer thread - only one thread is needed
         */
        executor = Executors.newFixedThreadPool(NUM_WRITER_THREADS);

        writers = new ArrayList<>();

        for (int i=0; i < NUM_WRITER_THREADS; i++) {
            MySQLWriterObject obj = new MySQLWriterObject(cfg);

            writers.add(obj);

            executor.submit(obj.writerThread);
        }
    }

    /**
     * Shutdown this thread and its threads
     */
    public void shutdown() {
        logger.debug("MySQL consumer thread shutting down");

        for (MySQLWriterObject obj: writers)
            obj.writerThread.shutdown();

        if (executor != null) executor.shutdown();

        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                logger.warn("Timed out waiting for writer thread to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted during shutdown, exiting uncleanly");
        }

        close_consumer();

        synchronized (running) {
            running = false;
        }
    }

    private void close_consumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }

    }

    /**
     * Connect to Kafka
     *
     * @return  True if connected, false if not connected
     */
    private boolean connect() {
        boolean status = false;

        try {
            close_consumer();

            consumer = new KafkaConsumer<>(this.props);
            logger.info("Connected to kafka, subscribing to topics");

            org.apache.kafka.clients.consumer.ConsumerRebalanceListener rebalanceListener =
                    new ConsumerRebalanceListener(consumer);

//                consumer.subscribe(topics, rebalanceListener);
//
//                for (String topic : topics) {
//                    logger.info("Subscribed to topic: %s", topic);
//                }

            status = true;

        } catch (ConfigException ex) {
            logger.error("Config Exception: %s", ex.getMessage());

        } catch (KafkaException ex) {
            logger.error("Exception: %s", ex.getMessage(), ex);

        } finally {
            return status;
        }

    }

    private void pause() {
        consumer.pause(consumer.assignment());
    }

    private void resume() {
        consumer.resume(consumer.paused());
    }

    private void resumePausedTopics() {
        if (pausedTopics.size() > 0) {
            logger.info("Resumed %d paused topics", pausedTopics.size());
            consumer.resume(pausedTopics);
            pausedTopics.clear();
        }
    }

    private void pauseUnicastPrefix() {
        Set<TopicPartition> topics = consumer.assignment();
        last_paused_time = System.currentTimeMillis();

        for (TopicPartition topic: topics) {
            if (topic.topic().equalsIgnoreCase("openbmp.parsed.unicast_prefix")) {
                if (! pausedTopics.contains(topic)) {
                    logger.info("Paused openbmp.parsed.unicast_prefix");
                    pausedTopics.add(topic);
                    consumer.pause(pausedTopics);
                }
                break;
            }
        }
    }

    /**
     * Run the thread
     */
    public void run() {
        boolean unicast_prefix_paused = false;

        logger.info("Consumer started");

        for (MySQLWriterObject obj: writers) {
            if (!obj.writerThread.isDbConnected()) {
                logger.warn("Ignoring request to run thread since DB connection couldn't be established");
                return;
            }
        }

        if (connect() == false) {
            logger.error("Failed to connect to Kafka, consumer exiting");
            running = false;
        } else {
            logger.debug("Conected and now consuming messages from kafka");
            running = true;
        }


        /*
         * Continuously read from Kafka stream and parse messages
         */
        Map<String, String> query;
        long prev_time = System.currentTimeMillis();
        long subscribe_prev_timestamp = 0L;

        while (true) {

            if (running == false) {
                try {
                    Thread.sleep(1000);

                } catch (InterruptedException e) {
                    break;
                }

                running = connect();
                continue;
            }

            // Subscribe to topics if needed
            if (! topics_all_subscribed) {
                subscribe_prev_timestamp = subscribe_topics(subscribe_prev_timestamp);

            } else if (pausedTopics.size() > 0 && (System.currentTimeMillis() - last_paused_time) > 90000) {
                logger.info("Resumed paused %d topics", pausedTopics.size());
                consumer.resume(pausedTopics);
                pausedTopics.clear();
            }

            try {
                ConsumerRecords<String, String> records = consumer.poll(100);

                if (records == null || records.count() <= 0)
                    continue;

                /*
                 * Pause collection so that consumer.poll() doesn't fetch but will send heartbeats
                 */
                pause();

                for (ConsumerRecord<String, String> record : records) {
                    messageCount = messageCount.add(BigInteger.ONE);

                    //Extract the Headers and Content from the message.
                    Message message = new Message(record.value());

                    Base obj = null;
                    Query dbQuery = null;

                    /*
                     * Parse the data based on topic
                     */
                    query = new HashMap<String, String>();
                    if (record.topic().equals("openbmp.parsed.collector")) {
                        logger.trace("Parsing collector message");
                        collector_msg_count++;

                        Collector collector = new Collector(message.getContent());
                        CollectorQuery collectorQuery = new CollectorQuery(collector.getRowMap());
                        obj = collector;
                        dbQuery = collectorQuery;

                        last_collector_msg_time = System.currentTimeMillis();

                        // Disconnect the routers
                        String sql = collectorQuery.genRouterCollectorUpdate(routerConMap);

                        if (sql != null && !sql.isEmpty()) {
                            logger.debug("collectorUpdate: %s", sql);

                            Map<String, String> router_update = new HashMap<>();
                            router_update.put("query", sql);

                            sendToWriter(record.key(), router_update);

                        }

                    } else if (record.topic().equals("openbmp.parsed.router")) {
                        logger.trace("Parsing router message");
                        router_msg_count++;

                        Router router = new Router(message.getVersion(), message.getContent());
                        RouterQuery routerQuery = new RouterQuery(message, router.getRowMap());
                        obj = router;
                        dbQuery = routerQuery;

                        pauseUnicastPrefix();

                        // Disconnect the peers
                        String sql = routerQuery.genPeerRouterUpdate(routerConMap);

                        if (sql != null && !sql.isEmpty()) {
                            logger.debug("RouterUpdate = %s", sql);

                            Map<String, String> peer_update = new HashMap<>();
                            peer_update.put("query", sql);

                            sendToWriter(record.key(), peer_update);
                        }

                    } else if (record.topic().equals("openbmp.parsed.peer")) {
                        logger.trace("Parsing peer message");
                        peer_msg_count++;

                        Peer peer = new Peer(message.getVersion(), message.getContent());
                        PeerQuery peerQuery = new PeerQuery(peer.getRowMap());
                        obj = peer;
                        dbQuery = peerQuery;

                        pauseUnicastPrefix();

                        // Add the withdrawn
                        Map<String, String> rib_update = new HashMap<String, String>();
                        rib_update.put("query", peerQuery.genRibPeerUpdate());

                        logger.debug("Processed peer %s / %s", peerQuery.genValuesStatement(), peerQuery.genRibPeerUpdate());
                        sendToWriter(record.key(), rib_update);

                    } else if (record.topic().equals("openbmp.parsed.base_attribute")) {
                        logger.trace("Parsing base_attribute message");
                        base_attribute_msg_count++;

                        BaseAttribute attr_obj = new BaseAttribute(message.getContent());
                        BaseAttributeQuery baseAttrQuery = new BaseAttributeQuery(attr_obj.getRowMap());
                        obj = attr_obj;
                        dbQuery = baseAttrQuery;

                    } else if (record.topic().equals("openbmp.parsed.unicast_prefix")) {
                        logger.trace("Parsing unicast_prefix message");
                        unicast_prefix_msg_count++;

                        obj = new UnicastPrefix(message.getVersion(), message.getContent());
                        dbQuery = new UnicastPrefixQuery(obj.getRowMap());

                        // Mark previous entries as withdrawn before update
                        Map<String, String> withdraw_update = new HashMap<String, String>();
                        withdraw_update.put("query",
                                ((UnicastPrefixQuery)dbQuery).genAsPathAnalysisWithdrawUpdate());

                        sendToWriter(record.key(), withdraw_update);

                        // Add as_path_analysis entries
                        addBulkQuerytoWriter(record.key(),
                                ((UnicastPrefixQuery)dbQuery).genAsPathAnalysisStatement(),
                                ((UnicastPrefixQuery)dbQuery).genAsPathAnalysisValuesStatement());

                    } else if (record.topic().equals("openbmp.parsed.l3vpn")) {
                        logger.trace("Parsing L3VPN prefix message");
                        l3vpn_prefix_msg_count++;

                        obj = new L3VpnPrefix(message.getVersion(), message.getContent());
                        dbQuery = new L3VpnPrefixQuery(obj.getRowMap());

                    } else if (record.topic().equals("openbmp.parsed.bmp_stat")) {
                        logger.trace("Parsing bmp_stat message");
                        stat_msg_count++;

                        obj = new BmpStat(message.getContent());
                        dbQuery = new BmpStatQuery(obj.getRowMap());

                    } else if (record.topic().equals("openbmp.parsed.ls_node")) {
                        logger.trace("Parsing ls_node message");
                        ls_node_msg_count++;

                        obj = new LsNode(message.getVersion(), message.getContent());
                        dbQuery = new LsNodeQuery(obj.getRowMap());

                    } else if (record.topic().equals("openbmp.parsed.ls_link")) {
                        logger.trace("Parsing ls_link message");
                        ls_link_msg_count++;

                        obj = new LsLink(message.getVersion(), message.getContent());
                        dbQuery = new LsLinkQuery(obj.getRowMap());

                    } else if (record.topic().equals("openbmp.parsed.ls_prefix")) {
                        logger.trace("Parsing ls_prefix message");
                        ls_prefix_msg_count++;

                        obj = new LsPrefix(message.getVersion(), message.getContent());
                        dbQuery = new LsPrefixQuery(obj.getRowMap());

                    } else {
                        logger.debug("Topic %s not implemented, ignoring", record.topic());
                        return;
                    }

                    /*
                     * Add query to writer queue
                     */
                    if (obj != null) {
                        addBulkQuerytoWriter(record.key(), dbQuery.genInsertStatement(),
                                             dbQuery.genValuesStatement());
                    }
                }

                resume();

                // Below is to check/monitor the writer queues - may move to debug only
                if (System.currentTimeMillis() - prev_time > 10000) {

                    for (int i = 0; i < NUM_WRITER_THREADS; i++) {
                        if (writers.get(i).writerQueue.size() > 500) {
                            logger.info("Writer %d thread: assigned = %d, queue = %d", i,
                                        writers.get(i).assigned.size(),
                                        writers.get(i).writerQueue.size());
                        }
                    }

                    prev_time = System.currentTimeMillis();
                }

            } catch (Exception ex) {
                logger.warn("kafka consumer exception: ", ex);

                close_consumer();

                running = false;
            }

        }

        shutdown();
        logger.debug("MySQL consumer thread finished");
    }

    private void sendToWriter(String key, Map<String, String> query) {
        MySQLWriterObject obj = writers.get(0);
        boolean found = false;

        if (!obj.assigned.containsKey(key)) {
            // Choose writer by lowest number of assigned record keys
            for (int i = 1; i < NUM_WRITER_THREADS; i++) {
                if (writers.get(i).assigned.containsKey(key)) {
                    obj = writers.get(i);
                    found = true;
                    break;
                }
                else {
                    if (obj.assigned.size() > writers.get(i).assigned.size())
                        obj = writers.get(i);
                }
            }
        } else {
            found = true;
        }

        if (! found) {
            obj.assigned.put(key, 1);
        }

        while (obj.writerQueue.offer(query) == false) {

            consumer.poll(1);
            try {
                Thread.sleep(5);
            } catch (Exception ex) {
                break;
            }
        }
    }

    /**
     * Add bulk query to writer
     *
     * \details This method will add the bulk object to the writer.
     *
     * @param key           Message key in kafka, such as the hash id
     * @param statement     String array statement from Query.getInsertStatement()
     * @param values        Values string from Query.getValuesStatement()
     */
    private void addBulkQuerytoWriter(String key, String [] statement, String values) {
        Map<String, String> query = new HashMap<>();

        try {
            if (values.length() > 0) {
                // Add statement and value to query map
                query.put("prefix", statement[0]);
                query.put("suffix", statement[1]);
                query.put("value", values);

                // block if space is not available
                sendToWriter(key, query);
            }
        } catch (Exception ex) {
            logger.info("Get values Exception: ", ex);
        }

    }

    /**
     * Method will subscribe to pending topics
     *
     * @param prev_timestamp        Previous timestamp that topics were subscribed.
     *
     * @return Time in milliseconds that the topic was last subscribed
     */
    private long subscribe_topics(long prev_timestamp) {
        long sub_timestamp = prev_timestamp;

        if (topics_subscribed_count < topics.size()) {

            if ((System.currentTimeMillis() - prev_timestamp) >= SUBSCRIBE_INTERVAL_MILLI) {
                List<String> subTopics = new ArrayList<>();

                for (int i=0; i <= topics_subscribed_count; i++) {
                    subTopics.add(topics.get(i));
                }

                consumer.commitSync();
                consumer.subscribe(subTopics, rebalanceListener);

                logger.info("Subscribed to topic: %s", topics.get(topics_subscribed_count));

                topics_subscribed_count++;

                sub_timestamp = System.currentTimeMillis();
            }
        } else {
            topics_all_subscribed = true;
        }

        return sub_timestamp;
    }

    public synchronized boolean isRunning() { return running; }
    public synchronized BigInteger getMessageCount() { return messageCount; }
    public synchronized Integer getQueueSize() {
        Integer qSize = 0;

        for (MySQLWriterObject obj: writers) {
            qSize += obj.writerQueue.size();
        }

        return qSize;
    }
    public synchronized Long getLast_collector_msg_time() { return last_collector_msg_time; }

    public long getCollector_msg_count() {
        return collector_msg_count;
    }

    public long getRouter_msg_count() {
        return router_msg_count;
    }

    public long getPeer_msg_count() {
        return peer_msg_count;
    }

    public long getBase_attribute_msg_count() {
        return base_attribute_msg_count;
    }

    public long getL3vpn_prefix_msg_count() {
        return l3vpn_prefix_msg_count;
    }

    public long getUnicast_prefix_msg_count() {
        return unicast_prefix_msg_count;
    }

    public long getLs_node_msg_count() {
        return ls_node_msg_count;
    }

    public long getLs_link_msg_count() {
        return ls_link_msg_count;
    }

    public long getLs_prefix_msg_count() {
        return ls_prefix_msg_count;
    }

    public long getStat_msg_count() {
        return stat_msg_count;
    }
}
