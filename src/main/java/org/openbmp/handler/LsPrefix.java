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
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Format class for ls_prefix parsed messages (openbmp.parsed.ls_prefix)
 *
 * Schema Version: 1
 *
 */
public class LsPrefix extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public LsPrefix(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "base_attr_hash", "router_hash", "router_ip", "peer_hash", "peer_ip",
                                      "peer_asn", "timestamp", "igp_router_id", "router_id", "routing_id", "ls_id",
                                      "ospf_area_id", "isis_area_id", "protocol", "as_path", "local_pref", "med", "nexthop",
                                      "local_node_hash", "mt_id", "ospf_route_type", "igp_flags", "route_tag",
                                      "ext_route_tag", "ospf_fwd_addr", "igp_metric", "prefix", "prefix_len"};

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
                new ParseNullAsEmpty(),         // ospf_area_id
                new ParseNullAsEmpty(),         // isis_area_id
                new ParseNullAsEmpty(),         // protocol
                new ParseNullAsEmpty(),         // as_path
                new ParseLongEmptyAsZero(),     // local_pref
                new ParseLongEmptyAsZero(),     // med
                new ParseNullAsEmpty(),         // nexthop
                new ParseNullAsEmpty(),         // local_node_hash
                new ParseNullAsEmpty(),         // mt_id
                new ParseNullAsEmpty(),         // ospf_route_type
                new ParseNullAsEmpty(),         // igp_flags
                new ParseLongEmptyAsZero(),     // route_tag
                new ParseLongEmptyAsZero(),     // ext_route_tag
                new ParseNullAsEmpty(),         // ospf_fwd_addr
                new ParseLongEmptyAsZero(),     // igp_metric
                new NotNull(),                  // prefix
                new ParseInt()                  // prefix_len
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
        String [] stmt = { " REPLACE INTO ls_prefixes (hash_id,peer_hash_id, path_attr_hash_id,id,local_node_hash_id," +
                           "mt_id,protocol,prefix,prefix_len,prefix_bin,prefix_bcast_bin,ospf_route_type," +
                           "igp_flags,isIPv4,route_tag,ext_route_tag,metric,ospf_fwd_addr,isWithdrawn,timestamp) VALUES ",

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
            sb.append(rowMap.get(i).get("routing_id") + ",");
            sb.append("'" + rowMap.get(i).get("local_node_hash") + "',");
            sb.append("'" + rowMap.get(i).get("mt_id") + "',");
            sb.append("'" + rowMap.get(i).get("protocol") + "',");
            sb.append("'" + rowMap.get(i).get("prefix") + "',");
            sb.append(rowMap.get(i).get("prefix_len") + ",");

            sb.append("X'" + IpAddr.getIpHex((String) rowMap.get(i).get("prefix")) + "',");
            sb.append("X'" + IpAddr.getIpBroadcastHex((String)rowMap.get(i).get("prefix"), (Integer) rowMap.get(i).get("prefix_len")) + "',");

            sb.append("'" + rowMap.get(i).get("ospf_route_type") + "',");
            sb.append("'" + rowMap.get(i).get("igp_flags") + "',");

            if (IpAddr.isIPv4(rowMap.get(i).get("prefix").toString()) == true)
                sb.append("1,");
            else
                sb.append("0,");

            sb.append(rowMap.get(i).get("route_tag") + ",");
            sb.append(rowMap.get(i).get("ext_route_tag") + ",");
            sb.append(rowMap.get(i).get("igp_metric") + ",");
            sb.append("'" + rowMap.get(i).get("ospf_fwd_addr") + "',");

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "'");

            sb.append(')');
        }

        return sb.toString();
    }

}
