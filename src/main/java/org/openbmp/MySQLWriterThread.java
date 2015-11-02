package org.openbmp;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MySQL writer thread class
 *
 * Inserts messages in bulk and batch (multi-statement) into MySQL by reading
 *      the FIFO queue.
 */
public class MySQLWriterThread implements  Runnable {
    private final Integer MAX_BULK_STATEMENTS = 5000;           // Maximum number of bulk values/multi-statements to allow
    private final Integer MAX_BULK_WAIT_MS = 100;               // Maximum milliseconds to wait for bulk messages

    private static final Logger logger = LogManager.getFormatterLogger(MySQLWriterThread.class.getName());

    private final Object lock = new Object();                   // Lock for thread
    private Connection con;                                     // MySQL connection
    private Boolean dbConnected;                                // Indicates if DB is connected or not
    private Config cfg;
    private BlockingQueue<Map<String, String>> writerQueue;     // Reference to the writer FIFO queue
    private boolean run;

    /**
     * Constructor
     *
     * @param cfg       Configuration - e.g. DB credentials
     * @param queue     FIFO queue to read from
     */
    public MySQLWriterThread(Config cfg, BlockingQueue queue) {
        this.cfg = cfg;
        writerQueue = queue;
        run = true;

        con = null;
        dbConnected = false;

        /*
         * Establish connection to MySQL
         */
        try {
            con = DriverManager.getConnection(
                    "jdbc:mariadb://" + cfg.getDbHost() + "/" + cfg.getDbName() +
                            "?tcpKeepAlive=true&connectTimeout=30000&socketTimeout=15000&useCompression=true&autoReconnect=true&allowMultiQueries=true",
                    cfg.getDbUser(), cfg.getDbPw());

            logger.debug("Writer thread connected to mysql");

            synchronized (lock) {
                dbConnected = true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            logger.warn("Writer thread failed to connect to mysql");
        }
    }

    /**
     * Shutdown this thread
     */
    public synchronized void shutdown() {
        run = false;

        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run MySQL update query
     *
     * @param query         Query string to run
     * @param retries       Number of times to retry, zero means no retries
     */
    private void mysqlQueryUpdate(String query, int retries) {
        // Loop the request if broken pipe, connection timed out, or deadlock
        for (int i = 0; i <= retries; i++) {
            try {
                Statement stmt = con.createStatement();
                logger.debug("SQL Query retry = %d: %s", i, query);
                stmt.executeUpdate(query);
                i = retries;
                break;

            } catch (SQLException e) {
                logger.warn("SQL exception state: " + e.getSQLState());
                logger.warn("SQL exception: " + e.getMessage());
                logger.debug("query: " + query);
                //e.printStackTrace();

                if (!e.getMessage().contains("Broken pipe") || !e.getMessage().contains("Connection timed out") ||
                        !e.getMessage().contains("Deadlock found when trying") ) {
                    i = retries;
                    break;
                }
            }
        }
    }

    /**
     * Run the thread
     */
    public void run() {
        if (!dbConnected) {
            logger.debug("Will not run writer thread since DB isn't connected");
            return;
        }
        logger.debug("writer thread started");

        long cur_time = 0;
        long prev_time = System.currentTimeMillis();

        int bulk_count = 0;

        /*
         * bulk query map has a key of : <prefix|suffix>
         *      Prefix and suffix are from the query FIFO message.  Value is the VALUE to be inserted/updated/deleted
         */
        Map<String, String> bulk_query = new HashMap<String, String>();

        try {
            while (run) {
                cur_time = System.currentTimeMillis();

                /*
                 * Do insert/query if max wait/duration has been reached or if max statements have been reached.
                 */
                if (cur_time - prev_time > MAX_BULK_WAIT_MS ||
                        bulk_count >= MAX_BULK_STATEMENTS) {

                    if (bulk_count > 0) {
                        logger.debug("Max reached, doing insert: wait_ms=%d bulk_count=%d", cur_time - prev_time, bulk_count);

                        StringBuilder query = new StringBuilder();
                        // Loop through queries and add them as multi-statements
                        for (Map.Entry<String, String> entry : bulk_query.entrySet()) {
                            String key = entry.getKey().toString();

                            String value = entry.getValue();

                            String[] ins = key.split("[|]");

                            if (query.length() > 0)
                                query.append(';');

                            query.append(ins[0]);
                            query.append(' ');
                            query.append(value);
                            query.append(' ');

                            if (ins.length > 1 && ins[1] != null && ins[1].length() > 0)
                                query.append(ins[1]);
                        }

                        if (query.length() > 0) {
                            mysqlQueryUpdate(query.toString(), 5);
                        }

                        prev_time = System.currentTimeMillis();

                        bulk_count = 0;
                        bulk_query.clear();
                    }
                    else {
                        prev_time = System.currentTimeMillis();
                    }
                }

                // Get next query from queue
                Map<String, String> cur_query = writerQueue.poll(MAX_BULK_WAIT_MS, TimeUnit.MILLISECONDS);

                if (cur_query != null) {
                    if (cur_query.containsKey("prefix")) {
                        String key = cur_query.get("prefix") + "|" + cur_query.get("suffix");
                        ++bulk_count;

                        // merge the data to existing bulk map if already present
                        if (bulk_query.containsKey(key)) {
                            bulk_query.put(key, bulk_query.get(key).concat("," + cur_query.get("value")));
                        } else {
                            bulk_query.put(key, cur_query.get("value"));
                        }
                    }
                    else if (cur_query.containsKey("query")) {  // Null prefix means run query now, not in bulk
                        logger.debug("Non bulk query");
                        mysqlQueryUpdate(cur_query.get("query"), 3);
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.debug("Writer thread finished");
    }

    /**
     * Indicates if the DB is connected or not.
     *
     * @return True if DB is connected, False otherwise
     */
    public boolean isDbConnected() {
        boolean status;

        synchronized (lock) {
            status = dbConnected;
        }

        return status;
    }
}
