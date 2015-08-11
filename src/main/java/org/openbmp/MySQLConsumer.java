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
    private int m_threadNumber;
    private ExecutorService executor;
    private MySQLWriterThread writerThread;

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
     */
    public MySQLConsumer(KafkaStream stream, int threadNumber, Config cfg) {
        m_threadNumber = threadNumber;
        m_stream = stream;

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
            String data = msg.substring(data_pos+2);

            Base obj = null;

            /*
             * Parse the data based on topic
             */
            query = new HashMap<String, String>();
            if (topic.equals("openbmp.parsed.collector")) {
                logger.debug("Parsing collector message");

                obj = new Collector(data);
            }

            else if (topic.equals("openbmp.parsed.router")) {
                logger.debug("Parsing router message");

                obj = new Router(data);
            }

            else if (topic.equals("openbmp.parsed.peer")) {
                logger.debug("Parsing peer message");

                obj = new Peer(data);
            }

            else if (topic.equals("openbmp.parsed.base_attribute")) {
                logger.debug("Parsing base_attribute message");

                BaseAttribute attr_obj = new BaseAttribute(data);
                obj = attr_obj;

                // Add as_path_analysis entries
                String values = attr_obj.genAsPathAnalysisValuesStatement();

                if (values.length() > 0) {
                    Map<String,String> analysis_query = new HashMap<String, String>();
                    String [] ins = attr_obj.genAsPathAnalysisStatement();
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
            }

            else if (topic.equals("openbmp.parsed.unicast_prefix")) {
                logger.debug("Parsing unicast_prefix message");

                obj = new UnicastPrefix(data);
            }

            else if (topic.equals("openbmp.parsed.bmp_stat")) {
                logger.debug("Parsing bmp_stat message");

                obj = new BmpStat(data);
            }

            else if (topic.equals("openbmp.parsed.ls_node")) {
                logger.debug("Parsing ls_node message");

                obj = new LsNode(data);
            }

            else if (topic.equals("openbmp.parsed.ls_link")) {
                logger.debug("Parsing ls_link message");

                obj = new LsLink(data);
            }

            else if (topic.equals("openbmp.parsed.ls_prefix")) {
                logger.debug("Parsing ls_prefix message");

                obj = new LsPrefix(data);
            }

            else {
                logger.debug("Topic %s not implemented, ignoring", topic);
                return;
            }

            /*
             * Add query to writer queue
             */
            if (obj != null) {

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
}
