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
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDouble;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Format class for ls_link parsed messages (openbmp.parsed.ls_link)
 *
 * Schema Version: 1
 *
 */
public class LsLink extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public LsLink(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "base_attr_hash", "router_hash", "router_ip", "peer_hash", "peer_ip",
                                      "peer_asn", "timestamp", "igp_router_id", "router_id", "routing_id", "ls_id",
                                      "ospf_area_id", "isis_area_id", "protocol", "as_path", "local_pref", "med", "nexthop",
                                      "mt_id", "local_link_id", "remote_link_id", "intf_ip", "nei_ip", "igp_metric",
                                      "admin_group", "max_link_bw", "max_resv_bw", "unresv_bw", "te_default_metric",
                                      "link_protection", "mpls_proto_mask", "srlg", "link_name", "remote_node_hash",
                                      "local_node_hash"};

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
                new ParseNullAsEmpty(),         // mt_id
                new ParseLongEmptyAsZero(),     // local_link_id
                new ParseLongEmptyAsZero(),     // remote_link_id
                new ParseNullAsEmpty(),         // intf_ip
                new ParseNullAsEmpty(),         // nei_ip
                new ParseLongEmptyAsZero(),     // igp_metric
                new ParseLongEmptyAsZero(),     // admin_group
                new ParseNullAsEmpty(),         // max_link_bw
                new ParseNullAsEmpty(),         // max_resv_bw
                new ParseNullAsEmpty(),         // unresv_bw
                new ParseLongEmptyAsZero(),     // te_default_metric
                new ParseNullAsEmpty(),         // link_protection
                new ParseNullAsEmpty(),         // mpls_proto_mask
                new ParseNullAsEmpty(),         // srlg
                new ParseNullAsEmpty(),         // link_name
                new ParseNullAsEmpty(),         // remote_node_hash
                new ParseNullAsEmpty()          // local_node_hash
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
        String [] stmt = { " REPLACE  INTO ls_links (hash_id,peer_hash_id, path_attr_hash_id,id,mt_id,interface_addr," +
                           "neighbor_addr,isIPv4,protocol,local_link_id,remote_link_id,local_node_hash_id,remote_node_hash_id," +
                           "admin_group,max_link_bw,max_resv_bw,unreserved_bw,te_def_metric,protection_type,mpls_proto_mask," +
                           "igp_metric,srlg,name,isWithdrawn,timestamp) VALUES ",

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
            sb.append("0x" + rowMap.get(i).get("mt_id") + ",");
            sb.append("'" + rowMap.get(i).get("intf_ip") + "',");
            sb.append("'" + rowMap.get(i).get("nei_ip") + "',");


            if (IpAddr.isIPv4(rowMap.get(i).get("intf_ip").toString()) == true)
                sb.append("1,");
            else
                sb.append("0,");

            sb.append("'" + rowMap.get(i).get("protocol") + "',");
            sb.append(rowMap.get(i).get("local_link_id") + ",");
            sb.append(rowMap.get(i).get("remote_link_id") + ",");
            sb.append("'" + rowMap.get(i).get("local_node_hash") + "',");
            sb.append("'" + rowMap.get(i).get("remote_node_hash") + "',");
            sb.append(rowMap.get(i).get("admin_group") + ",");
            sb.append(rowMap.get(i).get("max_link_bw") + ",");
            sb.append(rowMap.get(i).get("max_resv_bw") + ",");
            sb.append("'" + rowMap.get(i).get("unresv_bw") + "',");
            sb.append(rowMap.get(i).get("te_default_metric") + ",");
            sb.append("'" + rowMap.get(i).get("link_protection") + "',");
            sb.append("'" + rowMap.get(i).get("mpls_proto_mask") + "',");
            sb.append(rowMap.get(i).get("igp_metric") + ",");
            sb.append("'" + rowMap.get(i).get("srlg") + "',");

            // Resolve IP address
            String hostname = "";
            if (rowMap.get(i).get("protocol").toString().contains("OSPF")) {
                hostname = IpAddr.resolveIp(rowMap.get(i).get("intf_ip").toString());

                if (rowMap.get(i).get("link_name").toString().length() <= 0)
                    sb.append("'" + hostname + "',");
                else
                    sb.append("'" + rowMap.get(i).get("link_name") + "',");

            }
            else {
                sb.append("'" + rowMap.get(i).get("link_name") + "',");
            }

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "'");

            sb.append(')');
        }

        return sb.toString();
    }

}
