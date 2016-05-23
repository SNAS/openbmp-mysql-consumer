/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.openbmp;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.message.MessageAndMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbmp.handler.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MySQL Consumer class
 *
 *   A thread to process a topic partition.  Supports all openbmp.parsed.* topics.
 */
public class MySQLConsumer implements Runnable {
    private final Integer FIFO_QUEUE_SIZE = 10000;  // Size of the FIFO queue

    private KafkaStream m_stream;
    private String m_topics;
    private int m_threadNumber;
    private ExecutorService executor;
    private MySQLWriterThread writerThread;
    private BigInteger messageCount;
    private Long last_collector_msg_time;


    private Map<String,Map<String, Integer>> routerConMap;

    /*
     * FIFO queue for SQL messages to be written/inserted
     *      Queue message:
     *          Object is a hash map where the key is:
     *              prefix:     Insert statement including the VALUES keyword
     *              suffix:     ON DUPLICATE KEY UPDATE suffix, can be empty if not used
     *              value:      Comma delimited set of VALUES
     */
    private BlockingQueue<Map<String, String>> writerQueue;


    private static final Logger logger = LogManager.getFormatterLogger(MySQLConsumer.class.getName());

    /**
     * Constructor
     *
     * @param stream               topic/partition stream
     * @param threadNumber         this tread nubmer, used for logging
     * @param cfg                  configuration
     * @param routerConMap         Hash of collectors/routers and connection counts
     */
    public MySQLConsumer(KafkaStream stream, int threadNumber, Config cfg, String topics,
                         Map<String,Map<String, Integer>> routerConMap) {

        m_threadNumber = threadNumber;
        m_stream = stream;
        m_topics = topics;
        messageCount = BigInteger.valueOf(0);
        this.routerConMap = routerConMap;

        /*
         * Start MySQL Writer thread - only one thread is needed
         */
        executor = Executors.newFixedThreadPool(1);
        writerQueue = new ArrayBlockingQueue(FIFO_QUEUE_SIZE);
        writerThread = new MySQLWriterThread(cfg, writerQueue);
        executor.submit(writerThread);
    }

    /**
     * Shutdown this thread and its threads
     */
    public void shutdown() {
        logger.debug("MySQL consumer thread %d shutting down", m_threadNumber);

        writerThread.shutdown();

        if (executor != null) executor.shutdown();
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                logger.warn("Timed out waiting for writer thread to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted during shutdown, exiting uncleanly");
        }


    }

    /**
     * Run the thread
     */
    public void run() {
        if (! writerThread.isDbConnected()) {
            logger.warn("Ignoring request to run thread %d since DB connection couldn't be established", m_threadNumber);
            return;
        }

        ConsumerIterator<byte[], byte[]> it = m_stream.iterator();

        logger.info("Started thread %d, waiting for messages...", m_threadNumber);

        /*
         * Continuously read from Kafka stream and parse messages
         */
        Map<String, String> query;
        long prev_time = System.currentTimeMillis();


        while (it.hasNext()) {

            // Get the message
            MessageAndMetadata<byte[], byte[]> cur = it.next();

            String topic = cur.topic();
            String msg = new String(cur.message());

            // Find the position where the headers end and the data begins
            int data_pos = msg.lastIndexOf("\n\n");

            if (data_pos < 1) {
                logger.info("skipping invalid message from topic: %s\n%s", topic, new String(cur.message()));
                continue;
            }

            /*
             * Parse the headers
             */
            String headers = msg.substring(0, data_pos);
            String data = msg.substring(data_pos + 2);

            Base obj = null;

            /*
             * Parse the data based on topic
             */
            query = new HashMap<String, String>();
            if (topic.equals("openbmp.parsed.collector")) {
                logger.debug("Parsing collector message");

                Collector collector = new Collector(data);
                obj=collector;

                last_collector_msg_time = System.currentTimeMillis();

                // Disconnect the routers
                String sql = collector.genRouterCollectorUpdate(routerConMap);

                if (sql != null && !sql.isEmpty()) {
                    logger.debug("collectorUpdate: %s", sql);

                    Map<String, String> router_update = new HashMap<>();
                    router_update.put("query", sql);

                    // block if space is not available
                    try {
                        logger.debug("Added router disconnect correction to queue: size = %d", writerQueue.size());
                        writerQueue.put(router_update);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            } else if (topic.equals("openbmp.parsed.router")) {
                logger.debug("Parsing router message");

                Router router = new Router(new Headers(headers), data);
                obj = router;

                // Disconnect the peers
                String sql = router.genPeerRouterUpdate(routerConMap);

                if (sql != null && !sql.isEmpty()) {
                    logger.debug("RouterUpdate = %s", sql);

                    Map<String, String> peer_update = new HashMap<>();
                    peer_update.put("query", sql);

                    // block if space is not available
                    try {
                        logger.debug("Added peer disconnect correction to queue: size = %d", writerQueue.size());
                        writerQueue.put(peer_update);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            } else if (topic.equals("openbmp.parsed.peer")) {
                logger.debug("Parsing peer message");

                Peer peer = new Peer(data);
                obj = peer;

                // Add the withdrawn
                Map<String, String> rib_update = new HashMap<String,String>();
                rib_update.put("query", peer.genRibPeerUpdate());

                // block if space is not available
                try {
                    logger.debug("Processed peer [%d] %s / %s", m_threadNumber, peer.genValuesStatement(), peer.genRibPeerUpdate());
                    logger.debug("Added peer rib update message to queue: size = %d", writerQueue.size());
                    writerQueue.put(rib_update);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else if (topic.equals("openbmp.parsed.base_attribute")) {
                logger.debug("Parsing base_attribute message");

                BaseAttribute attr_obj = new BaseAttribute(data);
                obj = attr_obj;

                // Add as_path_analysis entries
                String values = attr_obj.genAsPathAnalysisValuesStatement();

                if (values.length() > 0) {
                    Map<String, String> analysis_query = new HashMap<String, String>();
                    String[] ins = attr_obj.genAsPathAnalysisStatement();
                    analysis_query.put("prefix", ins[0]);
                    analysis_query.put("suffix", ins[1]);

                    analysis_query.put("value", values);

                    // block if space is not available
                    try {
                        logger.debug("Added as_path_analysis message to queue: size = %d", writerQueue.size());
                        writerQueue.put(analysis_query);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // add community_analysis
                values = attr_obj.genCommunityAnalysisValuesStatement();
                if (values.length() > 0) {
                    Map<String, String> analysis_query = new HashMap<>();
                    String[] ins = attr_obj.genCommunityAnalysisStatement();
                    analysis_query.put("prefix", ins[0]);
                    analysis_query.put("suffix", ins[1]);

                    analysis_query.put("value", values);
                    try {
                        logger.debug("Added community_analysis message to queue: size = %d", writerQueue.size());
                        writerQueue.put(analysis_query);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            } else if (topic.equals("openbmp.parsed.unicast_prefix")) {
                logger.debug("Parsing unicast_prefix message");

                obj = new UnicastPrefix(data);
            } else if (topic.equals("openbmp.parsed.bmp_stat")) {
                logger.debug("Parsing bmp_stat message");

                obj = new BmpStat(data);
            } else if (topic.equals("openbmp.parsed.ls_node")) {
                logger.debug("Parsing ls_node message");

                obj = new LsNode(data);
            } else if (topic.equals("openbmp.parsed.ls_link")) {
                logger.debug("Parsing ls_link message");

                obj = new LsLink(data);
            } else if (topic.equals("openbmp.parsed.ls_prefix")) {
                logger.debug("Parsing ls_prefix message");

                obj = new LsPrefix(data);
            } else {
                logger.debug("Topic %s not implemented, ignoring", topic);
                return;
            }

            /*
             * Add query to writer queue
             */
            if (obj != null) {
                messageCount = messageCount.add(BigInteger.ONE);

                String values = obj.genValuesStatement();

                if (values.length() > 0) {
                    // Add statement and value to query map
                    String[] ins = obj.genInsertStatement();
                    query.put("prefix", ins[0]);
                    query.put("suffix", ins[1]);
                    query.put("value", values);

                    // block if space is not available
                    try {
                        writerQueue.put(query);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (writerQueue.size() > 500 && System.currentTimeMillis() - prev_time > 10000) {
                logger.info("Thread %d writer queue is %d", m_threadNumber, writerQueue.size());
                prev_time = System.currentTimeMillis();
            }
        }

        shutdown();
        logger.debug("MySQL consumer thread %d finished", m_threadNumber);
    }

    public synchronized BigInteger getMessageCount() { return messageCount; }
    public synchronized Integer getQueueSize() { return writerQueue.size(); }
    public synchronized String getTopics() { return m_topics; }
    public synchronized Long getLast_collector_msg_time() { return last_collector_msg_time; }
}
