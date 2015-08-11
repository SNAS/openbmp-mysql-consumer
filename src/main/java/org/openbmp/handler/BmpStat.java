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
 * Format class for bmp_stat parsed messages (openbmp.parsed.bmp_stat)
 *
 * Schema Version: 1
 *
 */
public class BmpStat extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public BmpStat(String data) {
        super();
        headerNames = new String [] { "action", "seq", "router_hash", "router_ip", "peer_hash", "peer_ip",
                                      "peer_asn", "timestamp", "rejected", "known_dup_updates", "known_dup_withdraws",
                                      "invalid_cluster_list", "invalid_as_path", "invalid_originator",
                                      "invalid_as_confed", "pre_policy", "post_policy"};

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
                new NotNull(),          // hash
                new NotNull(),          // router_ip
                new NotNull(),          // peer_hash
                new NotNull(),          // peer_ip,
                new ParseLong(),        // peer_asn
                new ParseTimestamp(),   // Timestamp
                new NotNull(),          // rejected
                new NotNull(),          // known_dup_updates
                new NotNull(),          // known_dup_withdraws
                new NotNull(),          // invalid_cluster_list
                new NotNull(),          // invalid_as_path
                new NotNull(),          // invalid_originator
                new NotNull(),          // invalid_as_confed
                new NotNull(),          // pre_policy
                new NotNull()           // post_policy
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
        String [] stmt = { " INSERT IGNORE INTO stat_reports (peer_hash_id,timestamp,prefixes_rejected,known_dup_prefixes,known_dup_withdraws," +
                           "updates_invalid_by_cluster_list,updates_invalid_by_as_path_loop,updates_invalid_by_originagtor_id," +
                           "updates_invalid_by_as_confed_loop,num_routes_adj_rib_in,num_routes_local_rib) VALUES ",

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
            sb.append("'" + rowMap.get(i).get("peer_hash") + "',");
            sb.append("'" + rowMap.get(i).get("timestamp") + "',");
            sb.append(rowMap.get(i).get("rejected") + ",");
            sb.append(rowMap.get(i).get("known_dup_updates") + ",");
            sb.append(rowMap.get(i).get("known_dup_withdraws") + ",");
            sb.append(rowMap.get(i).get("invalid_cluster_list") + ",");
            sb.append(rowMap.get(i).get("invalid_as_path") + ",");
            sb.append(rowMap.get(i).get("invalid_originator") + ",");
            sb.append(rowMap.get(i).get("invalid_as_confed") + ",");
            sb.append(rowMap.get(i).get("pre_policy") + ",");
            sb.append(rowMap.get(i).get("post_policy") + "");

            sb.append(')');
        }

        return sb.toString();
    }

}
