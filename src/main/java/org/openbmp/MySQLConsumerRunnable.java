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
import java.util.regex.Pattern;

/**
 * MySQL Consumer class
 *
 *   A thread to process a topic partition.  Supports all openbmp.parsed.* topics.
 */
public class MySQLConsumerRunnable implements Runnable {

    private enum ThreadType {
        THREAD_DEFAULT(0),
        THREAD_ATTRIBUTES(1),
        THRAED_AS_PATH_ANALYSIS(2);

        private final int value;

        private ThreadType(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    private static final Logger logger = LogManager.getFormatterLogger(MySQLConsumerRunnable.class.getName());
    private Boolean running;

    private ExecutorService executor;
    private Long last_collector_msg_time;
    private Long last_writer_thread_chg_time;


    private KafkaConsumer<String, String> consumer;
    private ConsumerRebalanceListener rebalanceListener;
    private Config cfg;
    private Map<String,Map<String, Integer>> routerConMap;

    private int topics_subscribed_count;
    private boolean topics_all_subscribed;
    private List<Pattern> topic_patterns;
    private StringBuilder topic_regex_pattern;

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
     * Writers thread map
     *      Key = Type of thread
     *      Value = List of writers
     *
     * Each writer object has the following:
     *      Connection to MySQL
     *      FIFO msg queue
     *      Map of assigned record keys to the writer
     */
    private final Map<ThreadType, List<MySQLWriterObject>> writer_thread_map;


    /**
     * Constructor
     *
     * @param cfg                  Configuration from cli/config file
     * @param routerConMap         Persistent router state tracking
     */
    public MySQLConsumerRunnable(Config cfg,
                                 Map<String,Map<String, Integer>> routerConMap) {


        writer_thread_map = new HashMap<>();
        last_writer_thread_chg_time = 0L;

        messageCount = BigInteger.valueOf(0);
        this.cfg = cfg;
        this.routerConMap = routerConMap;

        this.running = true;

        pausedTopics = new HashSet<>();
        last_paused_time = 0L;

        /*
         * It's imperative to first process messages from some topics before subscribing to others.
         *    When connecting to Kafka, topics will be subscribed at an interval.  When the
         *    topics_subscribe_count is equal to the topics size, then all topics have been subscribed to.
         */
        this.topics_subscribed_count = 0;
        this.topics_all_subscribed = false;
        this.topic_patterns = new LinkedList<>();

        // Convert to list so that we can access items by index.
        for (Iterator<Pattern> it = cfg.getKafka_topic_patterns().iterator(); it.hasNext(); ) {
            this.topic_patterns.add(it.next());
        }

        this.topic_regex_pattern = new StringBuilder();

        this.rebalanceListener = new ConsumerRebalanceListener(consumer);

        /*
         * Start MySQL Writer thread - one thread per type
         */
        executor = Executors.newFixedThreadPool(cfg.getWriter_max_threads_per_type() * ThreadType.values().length);

        // Init the list of threads for each thread type
        for (ThreadType t: ThreadType.values()) {
            MySQLWriterObject obj = new MySQLWriterObject(cfg);

            writer_thread_map.put(t, new ArrayList<MySQLWriterObject>());

            writer_thread_map.get(t).add(obj);
            executor.submit(obj.writerThread);
        }
    }

    /**
     * Shutdown this thread and its threads
     */
    public void shutdown() {
        logger.debug("MySQL consumer thread shutting down");

        for (ThreadType t: ThreadType.values()) {
            List<MySQLWriterObject> writers = writer_thread_map.get(t);
            for (MySQLWriterObject obj: writers) {
                obj.writerThread.shutdown();
            }
        }

        if (executor != null) executor.shutdown();

        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                logger.warn("Timed out waiting for writer thread to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted during shutdown, exiting uncleanly");
        }

        synchronized (running) {
            running = false;
        }

        close_consumer();
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

            consumer = new KafkaConsumer<>(cfg.getKafka_consumer_props());
            logger.info("Connected to kafka, subscribing to topics");

            org.apache.kafka.clients.consumer.ConsumerRebalanceListener rebalanceListener =
                    new ConsumerRebalanceListener(consumer);

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

        for (ThreadType t: ThreadType.values()) {
            List<MySQLWriterObject> writers = writer_thread_map.get(t);
            for (MySQLWriterObject obj: writers) {
                if (!obj.writerThread.isDbConnected()) {
                    logger.warn("Ignoring request to run thread since DB connection couldn't be established");
                    return;
                }
            }
        }

        if (connect() == false) {
            logger.error("Failed to connect to Kafka, consumer exiting");

            synchronized (running) {
                running = false;
            }

            return;
        } else {
            logger.debug("Conected and now consuming messages from kafka");

            synchronized (running) {
                running = true;
            }
        }


        /*
         * Continuously read from Kafka stream and parse messages
         */
        Map<String, String> query;
        long prev_time = System.currentTimeMillis();
        long subscribe_prev_timestamp = 0L;

        while (running) {

            // Subscribe to topics if needed
            if (! topics_all_subscribed) {
                subscribe_prev_timestamp = subscribe_topics(subscribe_prev_timestamp);

            } /* else if (pausedTopics.size() > 0 && (System.currentTimeMillis() - last_paused_time) > 90000) {
                logger.info("Resumed paused %d topics", pausedTopics.size());
                consumer.resume(pausedTopics);
                pausedTopics.clear();
            } */

            try {
                ConsumerRecords<String, String> records = consumer.poll(100);

                if (records == null || records.count() <= 0)
                    continue;

                /*
                 * Pause collection so that consumer.poll() doesn't fetch but will send heartbeats
                 */
                pause();

                ThreadType thread_type;
                for (ConsumerRecord<String, String> record : records) {
                    messageCount = messageCount.add(BigInteger.ONE);

                    //Extract the Headers and Content from the message.
                    Message message = new Message(record.value());

                    Base obj = null;
                    Query dbQuery = null;
                    thread_type = ThreadType.THREAD_DEFAULT;

                    /*
                     * Parse the data based on topic
                     */
                    query = new HashMap<String, String>();
                    if (message.getType().equalsIgnoreCase("collector") || record.topic().equals("openbmp.parsed.collector")) {
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

                            sendToWriter(record.key(), router_update, ThreadType.THREAD_DEFAULT);

                        }

                    } else if (message.getType().equalsIgnoreCase("router") || record.topic().equals("openbmp.parsed.router")) {
                        logger.trace("Parsing router message");
                        router_msg_count++;

                        Router router = new Router(message.getVersion(), message.getContent());
                        RouterQuery routerQuery = new RouterQuery(message, router.getRowMap());
                        obj = router;
                        dbQuery = routerQuery;

                        //pauseUnicastPrefix();

                        // Disconnect the peers
                        String sql = routerQuery.genPeerRouterUpdate(routerConMap);

                        if (sql != null && !sql.isEmpty()) {
                            logger.debug("RouterUpdate = %s", sql);

                            Map<String, String> peer_update = new HashMap<>();
                            peer_update.put("query", sql);

                            sendToWriter(record.key(), peer_update, ThreadType.THREAD_DEFAULT);
                        }

                    } else if (message.getType().equalsIgnoreCase("peer") || record.topic().equals("openbmp.parsed.peer")) {
                        logger.trace("Parsing peer message");
                        peer_msg_count++;

                        Peer peer = new Peer(message.getVersion(), message.getContent());
                        PeerQuery peerQuery = new PeerQuery(peer.getRowMap());
                        obj = peer;
                        dbQuery = peerQuery;

                        //pauseUnicastPrefix();

                        // Add the withdrawn
                        Map<String, String> rib_update = new HashMap<String, String>();
                        rib_update.put("query", peerQuery.genRibPeerUpdate());

                        logger.debug("Processed peer %s / %s", peerQuery.genValuesStatement(), peerQuery.genRibPeerUpdate());
                        sendToWriter(record.key(), rib_update, ThreadType.THREAD_DEFAULT);

                    } else if (message.getType().equalsIgnoreCase("base_attribute") || record.topic().equals("openbmp.parsed.base_attribute")) {
                        logger.trace("Parsing base_attribute message");
                        base_attribute_msg_count++;

                        thread_type = ThreadType.THREAD_ATTRIBUTES;

                        BaseAttribute attr_obj = new BaseAttribute(message.getContent());
                        BaseAttributeQuery baseAttrQuery = new BaseAttributeQuery(attr_obj.getRowMap());
                        obj = attr_obj;
                        dbQuery = baseAttrQuery;

                        if (! cfg.getDisable_as_path_indexing()) {
                            addBulkQuerytoWriter(record.key(),
                                    ((BaseAttributeQuery) dbQuery).genAsPathAnalysisStatement(),
                                    ((BaseAttributeQuery) dbQuery).genAsPathAnalysisValuesStatement(),
                                    ThreadType.THRAED_AS_PATH_ANALYSIS);
                        }

                    } else if (message.getType().equalsIgnoreCase("unicast_prefix") || record.topic().equals("openbmp.parsed.unicast_prefix")) {
                        logger.trace("Parsing unicast_prefix message");
                        unicast_prefix_msg_count++;

                        obj = new UnicastPrefix(message.getVersion(), message.getContent());
                        dbQuery = new UnicastPrefixQuery(obj.getRowMap());

                        // moved to base_attributes
//                        if (cfg.getDisable_as_path_indexing() == false) {
//
//                            addBulkQuerytoWriter(record.key(),
//                                    ((UnicastPrefixQuery)dbQuery).genAsPathAnalysisWithdrawStatement(),
//                                    ((UnicastPrefixQuery)dbQuery).genAsPathAnalysisWithdrawValuesStatement(),
//                                    ThreadType.THRAED_AS_PATH_ANALYSIS);
//
//                            // Add as_path_analysis entries
//                            addBulkQuerytoWriter(record.key(),
//                                    ((UnicastPrefixQuery) dbQuery).genAsPathAnalysisStatement(),
//                                    ((UnicastPrefixQuery) dbQuery).genAsPathAnalysisValuesStatement(),
//                                    ThreadType.THRAED_AS_PATH_ANALYSIS);
//                        }

                    } else if (message.getType().equalsIgnoreCase("l3vpn") ||record.topic().equals("openbmp.parsed.l3vpn")) {
                        logger.trace("Parsing L3VPN prefix message");
                        l3vpn_prefix_msg_count++;

                        obj = new L3VpnPrefix(message.getVersion(), message.getContent());
                        dbQuery = new L3VpnPrefixQuery(obj.getRowMap());

                    } else if (message.getType().equalsIgnoreCase("bmp_stat") || record.topic().equals("openbmp.parsed.bmp_stat")) {
                        logger.trace("Parsing bmp_stat message");
                        stat_msg_count++;

                        obj = new BmpStat(message.getContent());
                        dbQuery = new BmpStatQuery(obj.getRowMap());

                    } else if (message.getType().equalsIgnoreCase("ls_node") || record.topic().equals("openbmp.parsed.ls_node")) {
                        logger.trace("Parsing ls_node message");
                        ls_node_msg_count++;

                        obj = new LsNode(message.getVersion(), message.getContent());
                        dbQuery = new LsNodeQuery(obj.getRowMap());

                    } else if (message.getType().equalsIgnoreCase("ls_link") || record.topic().equals("openbmp.parsed.ls_link")) {
                        logger.trace("Parsing ls_link message");
                        ls_link_msg_count++;

                        obj = new LsLink(message.getVersion(), message.getContent());
                        dbQuery = new LsLinkQuery(obj.getRowMap());

                    } else if (message.getType().equalsIgnoreCase("ls_prefix") || record.topic().equals("openbmp.parsed.ls_prefix")) {
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
                                             dbQuery.genValuesStatement(), thread_type);
                    }
                }

                // Check writer threads
                prev_time = checkWriterThreads(prev_time);

                resume();


            } catch (Exception ex) {
                logger.warn("kafka consumer exception: ", ex);

                close_consumer();

                running = false;
            }

        }

        shutdown();
        logger.debug("MySQL consumer thread finished");
    }

    private void resetWriters(ThreadType thread_type) {
        List<MySQLWriterObject> writers = writer_thread_map.get(thread_type);

        if (writers != null) {

            logger.info("Thread type " + thread_type + ", draining queues to reset writers");
            for (MySQLWriterObject obj : writers) {
                int i = 0;
                while (obj.writerQueue.size() > 0) {
                    if (i >= 5000) {
                        i = 0;
                        consumer.poll(0);           // NOTE: consumer is paused already.

                        logger.info("drain queue writer size is " + obj.writerQueue.size());
                    }
                    ++i;

                    try {
                        Thread.sleep(1);
                    } catch (Exception ex) {
                        break;
                    }
                }

                obj.assigned.clear();
                obj.above_count = 0;
            }
        }
    }

    private void addWriterThread(ThreadType thread_type) {
        List<MySQLWriterObject> writers = writer_thread_map.get(thread_type);

        if (writers != null) {
            logger.info("Adding new writer thread for type " + thread_type);
            resetWriters(thread_type);

            MySQLWriterObject obj = new MySQLWriterObject(cfg);
            writers.add(obj);
            executor.submit(obj.writerThread);

            last_writer_thread_chg_time = System.currentTimeMillis();

            logger.info("Done adding new writer thread for type " + thread_type);

        }
    }

    private boolean rebalanceWriterThreads(ThreadType thread_type) {

        if ((System.currentTimeMillis() - last_writer_thread_chg_time) < cfg.getWriter_rebalance_millis()) {
            return false;
        }

        List<MySQLWriterObject> writers = writer_thread_map.get(thread_type);

        boolean lowThreads = false;
        boolean congestedThreads = false;

        for (MySQLWriterObject obj: writers) {

            if (obj.above_count > cfg.getWriter_allowed_over_queue_times() && obj.assigned.size() > 1) {
                congestedThreads = true;
            }
            else if (obj.above_count <= 0 && obj.writerQueue.size() <= 200) {
                lowThreads = true;
            }
        }

        if (congestedThreads && lowThreads) {
            logger.info("Rebalancing threads for type " + thread_type);
            resetWriters(thread_type);

            logger.info("DONE rebalancing threads for type " + thread_type);

            last_writer_thread_chg_time = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private void delWriterThread(ThreadType thread_type) {

        if ((System.currentTimeMillis() - last_writer_thread_chg_time) < cfg.getWriter_millis_thread_scale_back()) {
            return;
        }

        List<MySQLWriterObject> writers = writer_thread_map.get(thread_type);

        if (writers != null && writers.size() > 1) {
            last_writer_thread_chg_time = System.currentTimeMillis();

            logger.info("Deleting writer thread for type = " + thread_type);
            resetWriters(thread_type);

            writers.get(1).writerThread.shutdown();
            writers.remove(1);

            logger.info("Done deleting writer thread for type = " + thread_type);

        }

    }

    private long checkWriterThreads(long prev_time) {

        if (System.currentTimeMillis() - prev_time > 10000) {

            for (ThreadType t: ThreadType.values()) {
                List<MySQLWriterObject> writers = writer_thread_map.get(t);
                int i = 0;
                int threadsBelowThreshold = 0;

                if ( (rebalanceWriterThreads(t)) == true) {
                    continue;
                }
                else {

                    for (MySQLWriterObject obj : writers) {

                        if (obj.writerQueue.size() > 10000) {

                            if (obj.above_count > cfg.getWriter_allowed_over_queue_times()) {

                                if (writers.size() < cfg.getWriter_max_threads_per_type()) {
                                    // Add new thread
                                    logger.info("Writer %s %d: assigned = %d, queue = %d, above_count = %d, threads = %d : adding new thread",
                                            t.toString(), i,
                                            obj.assigned.size(),
                                            obj.writerQueue.size(),
                                            obj.above_count,
                                            writers.size());

                                    obj.above_count = 0;

                                    addWriterThread(t);
                                    break;

                                } else {
                                    // At max threads
                                    //obj.above_count++;

                                    logger.info("Writer %s %d: assigned = %d, queue = %d, above_count = %d, threads = %d, running max threads",
                                            t.toString(), i,
                                            obj.assigned.size(),
                                            obj.writerQueue.size(),
                                            obj.above_count,
                                            writers.size());
                                }
                            } else {
                                obj.above_count++;

                                // under above threshold
                                logger.info("Writer %s %d: assigned = %d, queue = %d, above_count = %d, threads = %d",
                                        t.toString(), i,
                                        obj.assigned.size(),
                                        obj.writerQueue.size(),
                                        obj.above_count,
                                        writers.size());
                            }

                        } else if (obj.writerQueue.size() < 100) {
                            obj.above_count = 0;
                            threadsBelowThreshold++;
                        }

                        i++;
                    }

                    if (threadsBelowThreshold >= writers.size()) {
                        delWriterThread(t);
                        break;
                    }
                }
            }

            return System.currentTimeMillis();
        }
        else {
            return prev_time;
        }
    }

    private void sendToWriter(String key, Map<String, String> query, ThreadType thread_type) {
        List<MySQLWriterObject> writers = writer_thread_map.get(thread_type);

        if (writers != null) {
            boolean newly_assigned = true;

            MySQLWriterObject found_obj= null;

            for (MySQLWriterObject obj: writers) {
                if (!obj.assigned.containsKey(key)) {
                    if (found_obj == null) {
                        found_obj = obj;
                    }
                    else if ((found_obj.writerQueue.size() > obj.writerQueue.size()
                            && obj.writerQueue.size() < 2000)
                            || (found_obj.assigned.size() > obj.assigned.size())) {
                        found_obj = obj;
                    }
                }
                else {
                    newly_assigned = false;
                    found_obj = obj;
                    break;
                }


            }

            if (newly_assigned) {
                found_obj.assigned.put(key, 1);
            }

            int i = 0;
            while (found_obj.writerQueue.offer(query) == false) {
                if (i >= 1000) {
//                    logger.info("send to writer congested, waiting. queue size: " + found_obj.writerQueue.size());
                    i = 0;
                    consumer.poll(0);           // NOTE: consumer is paused already.
                }
                ++i;

                try {
                    Thread.sleep(1);
                } catch (Exception ex) {
                    break;
                }
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
     * @param thread_type   Type of thread to use
     */
    private void addBulkQuerytoWriter(String key, String [] statement, String values, ThreadType thread_type) {
        Map<String, String> query = new HashMap<>();

        try {
            if (values.length() > 0) {
                // Add statement and value to query map
                query.put("prefix", statement[0]);
                query.put("suffix", statement[1]);
                query.put("value", values);

                // block if space is not available
                sendToWriter(key, query, thread_type);
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

        if (topics_subscribed_count < topic_patterns.size()) {

            if ((System.currentTimeMillis() - prev_timestamp) >= cfg.getTopic_subscribe_delay_millis()) {

                consumer.commitSync();

                if (topics_subscribed_count > 0)
                    topic_regex_pattern.append('|');

                topic_regex_pattern.append('(');
                topic_regex_pattern.append(topic_patterns.get(topics_subscribed_count));
                topic_regex_pattern.append(')');

                consumer.subscribe(Pattern.compile(topic_regex_pattern.toString()), rebalanceListener);

                logger.info("Subscribed to topic: %s", topic_patterns.get(topics_subscribed_count).pattern());
                logger.debug("Topics regex pattern: %s", topic_regex_pattern.toString());

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

        for (ThreadType t: ThreadType.values()) {
            List<MySQLWriterObject> writers = writer_thread_map.get(t);
            int i = 0;
            for (MySQLWriterObject obj: writers) {
                qSize += obj.writerQueue.size();
                i++;
            }
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
