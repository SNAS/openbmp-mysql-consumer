package org.openbmp.handler;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import org.openbmp.processor.ParseNullAsEmpty;
import org.openbmp.processor.ParseTimestamp;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Format class for collector parsed messages (openbmp.parsed.collector)
 *
 * Schema Version: 1
 *
 */
public class Collector extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public Collector(String data) {
        super();
        headerNames = new String [] { "action", "seq", "admin_id", "hash", "routers", "router_count", "timestamp" };

        parse(data);
    }

    /**
     * Processors used for each field.
     *
     * Order matters and must match the same order as defined in headerNames
     *
     * @return array of cell processors
     */
    protected CellProcessor[] getProcessors() {

        final CellProcessor[] processors = new CellProcessor[] {
                new NotNull(),          // action
                new ParseLong(),        // seq
                new NotNull(),          // admin id
                new NotNull(),          // hash
                new ParseNullAsEmpty(), // routers
                new ParseInt(),         // router count
                new ParseTimestamp()    // Timestamp
        };

        return processors;
    }

    /**
     * Generate MySQL insert/update statement, sans the values
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genInsertStatement() {
        String [] stmt = { " INSERT INTO collectors (hash_id,state,admin_id,routers,router_count,timestamp) VALUES ",
                           " ON DUPLICATE KEY UPDATE state=values(state),timestamp=values(timestamp),routers=values(routers),router_count=values(router_count)" };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert.
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genValuesStatement() {
        StringBuilder sb = new StringBuilder();

        for (int i=0; i < rowMap.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('(');
            sb.append("'" + rowMap.get(i).get("hash") + "',");
            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("stopped") ? "'down'" : "'up'") + ",");
            sb.append("'" + rowMap.get(i).get("admin_id") + "',");
            sb.append("'" + rowMap.get(i).get("routers") + "',");
            sb.append(rowMap.get(i).get("router_count") + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "'");
            sb.append(')');
        }

        return sb.toString();
    }

}
