package org.openbmp;
/*
 * Copyright (c) 2016-2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 *
 */
public class MySQLWriterObject {
    private final Integer FIFO_QUEUE_SIZE = 20000;                  // Number of messages in queue allowed

    ///< Map of assigned record keys to this writer object/thread
    Map<String, Integer> assigned;


    MySQLWriterRunnable writerThread;

    /**
     * FIFO queue for SQL messages to be written/inserted
     *      Queue message:
     *          Object is a hash map where the key is:
     *              prefix:     Insert statement including the VALUES keyword
     *              suffix:     ON DUPLICATE KEY UPDATE suffix, can be empty if not used
     *              value:      Comma delimited set of VALUES
     */
    BlockingQueue<Map<String, String>> writerQueue;

    /**
     * Constructor
     *
     * @param cfg            Configuration from cli/config file
     */
    MySQLWriterObject(Config cfg) {
        assigned = new HashMap<>();
        writerQueue = new ArrayBlockingQueue(FIFO_QUEUE_SIZE);
        writerThread = new MySQLWriterRunnable(cfg, writerQueue);
    }
}
