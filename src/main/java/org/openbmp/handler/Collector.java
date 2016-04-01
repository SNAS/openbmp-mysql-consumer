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

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Format class for collector parsed messages (openbmp.parsed.collector)
 *
 * Schema Version: 1
 *
 */
public class Collector extends Base {

    private static Map<String,TimerTask> heartbeatListeners;

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


    /**
     * Generate MySQL update statement to update router status
     *
     * Avoids faulty report of router status when collector gets disconnected
     *
     * @param routerConMap         Hash of collectors/routers and connection counts
     *
     * @return Multi statement update is returned, such as update ...; update ...;
     */
    public String genRouterCollectorUpdate( Map<String,Map<String, Integer>> routerConMap) {
        Boolean changed = Boolean.FALSE;
        StringBuilder sb = new StringBuilder();
        StringBuilder router_sql_in_list = new StringBuilder();
        router_sql_in_list.append("(");

        for (int i = 0; i < rowMap.size(); i++) {

            String action = (String) rowMap.get(i).get("action");

            if (i > 0 && sb.length() > 0)
                sb.append(';');

            if (action.equalsIgnoreCase("started") || action.equalsIgnoreCase("stopped")) {
                sb.append("UPDATE routers SET isConnected = False WHERE collector_hash_id = '");
                sb.append(rowMap.get(i).get("hash") + "'");

                // Collector start/stopped should always have an empty router set
                routerConMap.remove((String)rowMap.get(i).get("hash"));

            }
            else { // heartbeat or changed

                // Add concurrent connection map for collector if it does not exist already
                if (! routerConMap.containsKey((String)rowMap.get(i).get("hash"))) {
                    routerConMap.put((String)rowMap.get(i).get("hash"), new ConcurrentHashMap<String, Integer>());
                    changed = Boolean.TRUE;
                }

                String[] routerArray = ((String) rowMap.get(i).get("routers")).split("[ ]*,[ ]*");

                if (routerArray.length > 0) {
                    // Update the router list
                    Map<String, Integer> routerMap = routerConMap.get((String) rowMap.get(i).get("hash"));
                    routerMap.clear();

                    for (String router : routerArray) {

                        if (routerMap.containsKey(router)) {                    // Increment
                            routerMap.put(router, routerMap.get(router) + 1);
                        } else {                                                // new
                            if (routerMap.size() > 0) {
                                router_sql_in_list.append(" OR ");
                            }

                            router_sql_in_list.append(" ip_address = '");
                            router_sql_in_list.append(router);
                            router_sql_in_list.append("'");

                            routerMap.put(router, 1);
                        }
                    }

                    router_sql_in_list.append(")");

                    // Update routers if there's a change
                    if (changed && router_sql_in_list.length() > 2) {
                        if (sb.length() > 0) {
                            sb.append(";");
                        }

                        sb.append("UPDATE routers SET isConnected = True WHERE collector_hash_id = '" + rowMap.get(i).get("hash") + "' AND " + router_sql_in_list);
                    }
                }
            }
        }

        return sb.toString();
    }

}
