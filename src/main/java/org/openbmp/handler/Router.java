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
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Format class for router parsed messages (openbmp.parsed.router)
 *
 * Schema Version: 1
 *
 */
public class Router extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param headers Generated Headers object from the headers in the message
     * @param data
     */
    public Router(Headers headers, String data) {
        super();
        headerNames = new String [] { "action", "seq", "name", "hash", "ip_address", "description", "term_code",
                                      "term_reason", "init_data", "term_data", "timestamp" };

        this.headers = headers;
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
                new NotNull(),                      // action
                new ParseLong(),                    // seq
                new ParseNullAsEmpty(),             // name
                new NotNull(),                      // hash
                new NotNull(),                      // IP Address
                new ParseNullAsEmpty(),             // Description
                new Optional(new ParseInt()),       // Term code
                new ParseNullAsEmpty(),             // Term reason
                new ParseNullAsEmpty(),             // Init data
                new ParseNullAsEmpty(),             // Term data
                new ParseTimestamp()                // Timestamp
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
        String [] stmt = { " INSERT INTO routers (hash_id,name,ip_address,timestamp,isConnected,term_reason_code," +
                                  "term_reason_text,term_data,init_data,description,collector_hash_id) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp),isConnected=values(isConnected)," +
                                   "name=if(isConnected = 1, values(name), name)," +
                                   "description=values(description),init_data=values(init_data)," +
                                   "term_reason_code=values(term_reason_code),term_reason_text=values(term_reason_text)," +
                                   "collector_hash_id=values(collector_hash_id)" };
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
            sb.append("'" + rowMap.get(i).get("name") + "',");
            sb.append("'" + rowMap.get(i).get("ip_address") + "',");
            sb.append("'" + rowMap.get(i).get("timestamp") + "',");

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("term") ? 0 : 1) + ",");

            sb.append(rowMap.get(i).get("term_code") + ",");
            sb.append("'" + rowMap.get(i).get("term_reason") + "',");
            sb.append("'" + rowMap.get(i).get("term_data") + "',");
            sb.append("'" + rowMap.get(i).get("init_data") + "',");
            sb.append("'" + rowMap.get(i).get("description") + "',");
            sb.append("'" + headers.getCollector_hash_id() + "'");
            sb.append(')');
        }

        return sb.toString();
    }


    /**
     * Generate MySQL update statement to update peer status
     *
     * Avoids faulty report of peer status when router gets disconnected
     *
     * @param routerConMap         Hash of collectors/routers and connection counts
     *
     * @return Multi statement update is returned, such as update ...; update ...;
     */
    public String genPeerRouterUpdate( Map<String,Map<String, Integer>> routerConMap) {

        StringBuilder sb = new StringBuilder();

        List<Map<String, Object>> resultMap = new ArrayList<>();
        resultMap.addAll(rowMap);

        // Add the collector entry if router is seen before collector message
        if (! routerConMap.containsKey(headers.getCollector_hash_id())) {
            routerConMap.put(headers.getCollector_hash_id(), new ConcurrentHashMap<String, Integer>());
        }

        Map<String, Integer> routerMap = routerConMap.get(headers.getCollector_hash_id());

        for (int i = 0; i < rowMap.size(); i++) {

            if (((String) rowMap.get(i).get("action")).equalsIgnoreCase("first") || ((String) rowMap.get(i).get("action")).equalsIgnoreCase("init")) {
                if (sb.length() > 0)
                    sb.append(";");

                // Upon initial router message, we set the state of all peers to down since we will get peer UP's
                sb.append("UPDATE bgp_peers SET state = 0 WHERE router_hash_id = '");
                sb.append(rowMap.get(i).get("hash") + "'");

                // Collector changed/heartbeat messages maintain the routerMap, but timing might prevent an update
                //    so add the router if it doesn't exist already
                if (! routerMap.containsKey((String)rowMap.get(i).get("ip_address")) ) {
                    routerMap.put((String)rowMap.get(i).get("ip_address"), 1);

                } else {
                    // Increment the entry for the new connection
                    routerMap.put((String)rowMap.get(i).get("ip_address"),
                            routerMap.get((String)rowMap.get(i).get("ip_address")) + 1 );
                }

            }

            else if (((String) rowMap.get(i).get("action")).equalsIgnoreCase("term")) {
                // Update the router map to reflect the termination
                if (routerMap.containsKey((String)rowMap.get(i).get("ip_address")) ) {

                    // decrement connection count or remove the router entry on term
                    if (routerMap.get((String)rowMap.get(i).get("ip_address")) > 1) {
                        routerMap.put((String)rowMap.get(i).get("ip_address"),
                                routerMap.get((String)rowMap.get(i).get("ip_address")) - 1 );

                        // Suppress the term message since another connection exists
                        resultMap.remove(rowMap.get(i));

                    } else {
                        routerMap.remove((String)rowMap.get(i).get("ip_address"));
                    }
                }
            }
        }

        rowMap = resultMap;

        return sb.toString();
    }

}
