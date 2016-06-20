package org.openbmp.handler;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import org.openbmp.helpers.IpAddr;
import org.openbmp.processor.ParseLongEmptyAsZero;
import org.openbmp.processor.ParseNullAsEmpty;
import org.openbmp.processor.ParseTimestamp;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Format class for ls_node parsed messages (openbmp.parsed.ls_node)
 *
 * Schema Version: 1
 *
 */
public class LsNode extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public LsNode(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "base_attr_hash", "router_hash", "router_ip", "peer_hash", "peer_ip",
                                      "peer_asn", "timestamp", "igp_router_id", "router_id", "routing_id", "ls_id", "mt_id",
                                      "ospf_area_id", "isis_area_id", "protocol", "flags", "as_path", "local_pref",
                                      "med", "nexthop", "name" };

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
                new NotNull(),                  // action
                new ParseLong(),                // seq
                new NotNull(),                  // hash
                new NotNull(),                  // base_hash
                new NotNull(),                  // router_hash
                new NotNull(),                  // router_ip
                new NotNull(),                  // peer_hash
                new NotNull(),                  // peer_ip
                new ParseLong(),                // peer_asn
                new ParseTimestamp(),           // timestamp
                new ParseNullAsEmpty(),         // igp_router_id
                new ParseNullAsEmpty(),         // router_id
                new ParseNullAsEmpty(),         // routing_id
                new ParseLongEmptyAsZero(),     // ls_id
                new ParseNullAsEmpty(),         // mt_id
                new ParseNullAsEmpty(),         // ospf_area_id
                new ParseNullAsEmpty(),         // isis_area_id
                new ParseNullAsEmpty(),         // protocol
                new ParseNullAsEmpty(),         // flags
                new ParseNullAsEmpty(),         // as_path
                new ParseLongEmptyAsZero(),     // local_pref
                new ParseLongEmptyAsZero(),     // med
                new ParseNullAsEmpty(),         // nexthop
                new ParseNullAsEmpty()          // name
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
        String [] stmt = { " REPLACE  INTO ls_nodes (hash_id,peer_hash_id, path_attr_hash_id,asn,id,bgp_ls_id,igp_router_id," +
                           "ospf_area_id,protocol,router_id,isis_area_id,flags,name,mt_ids,isWithdrawn,timestamp) VALUES ",

                           " " };
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
            sb.append("'" + rowMap.get(i).get("peer_hash") + "',");
            sb.append("'" + rowMap.get(i).get("base_attr_hash") + "',");
            sb.append(rowMap.get(i).get("peer_asn") + ",");
            sb.append(rowMap.get(i).get("routing_id") + ",");
            sb.append(rowMap.get(i).get("ls_id") + ",");
            sb.append("'" + rowMap.get(i).get("igp_router_id") + "',");
            sb.append("'" + rowMap.get(i).get("ospf_area_id") + "',");
            sb.append("'" + rowMap.get(i).get("protocol") + "',");
            sb.append("'" + rowMap.get(i).get("router_id") + "',");
            sb.append("'" + rowMap.get(i).get("isis_area_id") + "',");
            sb.append("'" + rowMap.get(i).get("flags") + "',");

            // Resolve IP address
            String hostname = "";
            if (rowMap.get(i).get("protocol").toString().contains("OSPF"))
                hostname = IpAddr.resolveIp(rowMap.get(i).get("igp_router_id").toString());
            else
                hostname = IpAddr.resolveIp(rowMap.get(i).get("router_id").toString());

            if (rowMap.get(i).get("name").toString().length() <= 0)
                sb.append("'" + hostname + "',");
            else
                sb.append("'" + rowMap.get(i).get("name") + "',");

            sb.append("'" + rowMap.get(i).get("mt_id") + "',");

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "'");

            sb.append(')');
        }

        return sb.toString();
    }

}
