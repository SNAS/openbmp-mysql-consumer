package org.openbmp.handler;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import org.openbmp.processor.ParseLongEmptyAsZero;
import org.openbmp.processor.ParseNullAsEmpty;
import org.openbmp.processor.ParseTimestamp;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import scala.Int;

/**
 * Format class for base_attribute parsed messages (openbmp.parsed.base_attribute)
 *
 * Schema Version: 1
 *
 */
public class BaseAttribute extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public BaseAttribute(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "router_hash", "router_ip", "peer_hash", "peer_ip",
                                      "peer_asn", "timestamp", "origin", "as_path", "as_path_count", "origin_as",
                                      "nexthop", "med", "local_pref", "aggregator", "community_list", "ext_community_list",
                                      "cluster_list", "isAtomicAgg", "isNexthopIPv4", "originator_id" };

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
                new NotNull(),                      // peer_hash
                new NotNull(),                      // peer_ip
                new ParseLong(),                    // peer_asn
                new ParseTimestamp(),               // timestamp
                new ParseNullAsEmpty(),             // origin
                new ParseNullAsEmpty(),             // as_path
                new ParseLong(),                    // as_path_count
                new ParseLong(),                    // origin_as
                new ParseNullAsEmpty(),             // nexthop
                new ParseLong(),                    // med
                new ParseLong(),                    // local_pref
                new ParseNullAsEmpty(),             // aggregator
                new ParseNullAsEmpty(),             // community_list
                new ParseNullAsEmpty(),             // ext_community_list
                new ParseNullAsEmpty(),             // cluster_list
                new ParseLongEmptyAsZero(),         // isAtomicAgg
                new ParseLongEmptyAsZero(),         // isNexthopIPv4
                new ParseNullAsEmpty()              // originator_id
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
        String [] stmt = { " INSERT INTO path_attrs (hash_id,peer_hash_id,origin,as_path,origin_as,next_hop,med,local_pref," +
                                "isAtomicAgg,aggregator,community_list,ext_community_list," +
                                "cluster_list,originator_id,as_path_count,nexthop_isIPv4,timestamp) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
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
            sb.append("'" + rowMap.get(i).get("origin") + "',");
            sb.append("'" + rowMap.get(i).get("as_path") + "',");
            sb.append(rowMap.get(i).get("origin_as") + ",");
            sb.append("'" + rowMap.get(i).get("nexthop") + "',");
            sb.append(rowMap.get(i).get("med") + ",");
            sb.append(rowMap.get(i).get("local_pref") + ",");
            sb.append(rowMap.get(i).get("isAtomicAgg") + ",");
            sb.append("'" + rowMap.get(i).get("aggregator") + "',");
            sb.append("'" + rowMap.get(i).get("community_list") + "',");
            sb.append("'" + rowMap.get(i).get("ext_community_list") + "',");
            sb.append("'" + rowMap.get(i).get("cluster_list") + "',");
            sb.append("'" + rowMap.get(i).get("originator_id") + "',");
            sb.append(rowMap.get(i).get("as_path_count") + ",");
            sb.append(rowMap.get(i).get("isNexthopIPv4") + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "'");
            sb.append(')');
        }

        return sb.toString();
    }

    /**
     * Generate MySQL insert/update statement, sans the values for as_path_analysis
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genAsPathAnalysisStatement() {
        String [] stmt = {" INSERT IGNORE INTO as_path_analysis (asn,asn_left,asn_right,path_attr_hash_id, peer_hash_id)" +
                                    " VALUES ", "" };

                          //" ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert for as_path_analysis
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genAsPathAnalysisValuesStatement() {
        StringBuilder sb = new StringBuilder();

        /*
         * Iterate through the AS Path and extract out the left and right ASN for each AS within
         *     the AS PATH
         */
        for (int i=0; i < rowMap.size(); i++) {

            String as_path_str = ((String)rowMap.get(i).get("as_path")).trim();
            as_path_str = as_path_str.replaceAll("[{}]", "");
            String[] as_path = as_path_str.split(" ");

            //System.out.println("AS Path = " + as_path_str);

            Long left_asn = 0L;
            Long right_asn = 0L;
            Long asn = 0L;

            for (int i2=0; i2 < as_path.length; i2++) {
                if (as_path[i2].length() <= 0)
                    break;

                try {
                    asn = Long.valueOf(as_path[i2]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    break;
                }

                if (asn > 0 ) {
                    if (i2+1 < as_path.length) {

                        if (as_path[i2 + 1].length() <= 0)
                            break;

                        try {
                            right_asn = Long.valueOf(as_path[i2 + 1]);

                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            break;
                        }

                        if (right_asn.equals(asn)) {
                            continue;
                        }

                        if (sb.length() > 0)
                            sb.append(',');

                        sb.append("(" + asn + "," + left_asn + "," + right_asn + ",'" + rowMap.get(i).get("hash") + "','" +
                                rowMap.get(i).get("peer_hash") + "')");


                    } else {
                        // No more left in path
                        if (sb.length() > 0)
                            sb.append(',');

                        sb.append("(" + asn + "," + left_asn + ",0,'" + rowMap.get(i).get("hash") + "','" +
                                rowMap.get(i).get("peer_hash") + "')");
                        break;
                    }

                    left_asn = asn;
                }
            }
        }

        //System.out.println("AS insert: " + sb.toString());
        if (sb.length() <= 0)
            sb.append("");
        return sb.toString();
    }

    /**
     * Generate MySQL insert/update statement, sans the values for community_analysis
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genCommunityAnalysisStatement() {
        String [] stmt = {" INSERT IGNORE INTO community_analysis (community,part1,part2,path_attr_hash_id, peer_hash_id)" +
                " VALUES ", "" };

                //" ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert for community_analysis
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genCommunityAnalysisValuesStatement() {
        StringBuilder sb = new StringBuilder();

        /*
         * Iterate through the community list and extract part1 and part2,
         * which are separated by colons
         */
        for (int i=0; i < rowMap.size(); i++) {
            String communityString = ((String)rowMap.get(i).get("community_list")).trim();
            String[] communityList = communityString.split(" ");

            String path_attr_hash = (String) rowMap.get(i).get("hash");
            String peer_hash = (String) rowMap.get(i).get("peer_hash");

            for (int j = 0; j < communityList.length; j++) {
                if (communityList[j].length() <= 0) {
                    break;
                }

                Integer part1 = null, part2 = null;
                try {
                    String[] twoParts = communityList[j].split(":");
                    part1 = Integer.valueOf(twoParts[0]);
                    part2 = Integer.valueOf(twoParts[1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    break;
                }

                if (part1 != null && part2 != null) {
                    if (sb.length() > 0)
                        sb.append(",");

                    sb.append("('" + communityList[j] + "',"
                            + part1 + ","
                            + part2 + ",'"
                            + path_attr_hash + "','"
                            + peer_hash + "')"
                    );
                }
            }
        }

        //System.out.println("AS insert: " + sb.toString());
        if (sb.length() <= 0)
            sb.append("");
        return sb.toString();
    }
}
