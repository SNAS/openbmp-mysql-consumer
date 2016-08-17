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

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Format class for unicast_prefix parsed messages (openbmp.parsed.unicast_prefix)
 *
 * Schema Version: 1.3
 *
 */
public class UnicastPrefix extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public UnicastPrefix(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "router_hash", "router_ip", "base_attr_hash", "peer_hash",
                                      "peer_ip", "peer_asn", "timestamp", "prefix", "prefix_len", "isIPv4",
                                      "origin", "as_path", "as_path_count", "origin_as",
                                      "nexthop", "med", "local_pref", "aggregator", "community_list", "ext_community_list",
                                      "cluster_list", "isAtomicAgg", "isNexthopIPv4", "originator_id",
                                      "path_id", "labels", "isPrePolicy", "isAdjRibIn"};

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
                new NotNull(),                      // hash
                new NotNull(),                      // router hash
                new NotNull(),                      // router_ip
                new ParseNullAsEmpty(),             // base_attr_hash
                new NotNull(),                      // peer_hash
                new NotNull(),                      // peer_ip
                new ParseLong(),                    // peer_asn
                new ParseTimestamp(),               // timestamp
                new NotNull(),                      // prefix
                new ParseInt(),                     // prefix_len
                new ParseInt(),                     // isIPv4
                new ParseNullAsEmpty(),             // origin
                new ParseNullAsEmpty(),             // as_path
                new ParseLongEmptyAsZero(),         // as_path_count
                new ParseLongEmptyAsZero(),         // origin_as
                new ParseNullAsEmpty(),             // nexthop
                new ParseLongEmptyAsZero(),         // med
                new ParseLongEmptyAsZero(),         // local_pref
                new ParseNullAsEmpty(),             // aggregator
                new ParseNullAsEmpty(),             // community_list
                new ParseNullAsEmpty(),             // ext_community_list
                new ParseNullAsEmpty(),             // cluster_list
                new ParseLongEmptyAsZero(),         // isAtomicAgg
                new ParseLongEmptyAsZero(),         // isNexthopIPv4
                new ParseNullAsEmpty(),             // originator_id
                new ParseLongEmptyAsZero(),         // Path ID
                new ParseNullAsEmpty(),             // Labels
                new ParseLongEmptyAsZero(),         // isPrePolicy
                new ParseLongEmptyAsZero()          // isAdjRibIn
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
        String [] stmt = { " INSERT IGNORE INTO rib (hash_id,peer_hash_id,path_attr_hash_id,isIPv4," +
                           "origin_as,prefix,prefix_len,prefix_bin,prefix_bcast_bin,prefix_bits,timestamp," +
                           "isWithdrawn,path_id,labels,isPrePolicy,isAdjRibIn) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp)," +
                               "prefix_bits=values(prefix_bits)," +
                               "path_attr_hash_id=if(values(isWithdrawn) = 1, path_attr_hash_id, values(path_attr_hash_id))," +
                               "origin_as=if(values(isWithdrawn) = 1, origin_as, values(origin_as)),isWithdrawn=values(isWithdrawn)," +
                               "path_id=values(path_id), labels=values(labels)," +
                               "isPrePolicy=values(isPrePolicy), isAdjRibIn=values(isAdjRibIn) "
                        };
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
            sb.append(rowMap.get(i).get("isIPv4") + ",");
            sb.append(rowMap.get(i).get("origin_as") + ",");
            sb.append("'" + rowMap.get(i).get("prefix") + "',");
            sb.append(rowMap.get(i).get("prefix_len") + ",");

            sb.append("X'" + IpAddr.getIpHex((String) rowMap.get(i).get("prefix")) + "',");
            sb.append("X'" + IpAddr.getIpBroadcastHex((String) rowMap.get(i).get("prefix"), (Integer) rowMap.get(i).get("prefix_len")) + "',");
            sb.append("'" + IpAddr.getIpBits((String) rowMap.get(i).get("prefix")).substring(0,(Integer)rowMap.get(i).get("prefix_len")) + "',");

            sb.append("'" + rowMap.get(i).get("timestamp") + "',");
            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append(rowMap.get(i).get("path_id") + ",");
            sb.append("'" + rowMap.get(i).get("labels") + "',");
            sb.append(rowMap.get(i).get("isPrePolicy") + ",");
            sb.append(rowMap.get(i).get("isAdjRibIn"));

            sb.append(')');
        }

        return sb.toString();
    }



}
