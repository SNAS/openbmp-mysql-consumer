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
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseBool;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Format class for peer parsed messages (openbmp.parsed.peer)
 *
 * Schema Version: 1
 *
 */
public class Peer extends Base {

    /**
     * Handle the message by parsing it and storing the data in memory.
     *
     * @param data
     */
    public Peer(String data) {
        super();
        headerNames = new String [] { "action", "seq", "hash", "router_hash", "name", "remote_bgp_id", "router_ip",
                                      "timestamp", "remote_asn", "remote_ip", "peer_rd", "remote_port", "local_asn",
                                      "local_ip", "local_port", "local_bgp_id", "info_data", "adv_cap", "recv_cap",
                                      "remote_holddown", "adv_holddown", "bmp_reason", "bgp_error_code",
                                      "bgp_error_sub_code", "error_text", "isL3VPN", "isPrePolicy", "isIPv4"};

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
                new ParseNullAsEmpty(),             // name
                new NotNull(),                      // remote_bgp_id
                new NotNull(),                      // router_ip
                new ParseTimestamp(),               // Timestamp
                new ParseLong(),                    // remote_asn
                new NotNull(),                      // remote_ip
                new ParseNullAsEmpty(),             // peer_rd
                new ParseLongEmptyAsZero(),         // remote_port
                new ParseLongEmptyAsZero(),         // local_asn
                new ParseNullAsEmpty(),             // local_ip
                new ParseLongEmptyAsZero(),         // local_port
                new ParseNullAsEmpty(),             // local_bgp_id
                new ParseNullAsEmpty(),             // info_data
                new ParseNullAsEmpty(),             // adv_cap
                new ParseNullAsEmpty(),             // recv_cap,
                new ParseLongEmptyAsZero(),         // remote_holddown
                new ParseLongEmptyAsZero(),         // local_holddown
                new ParseLongEmptyAsZero(),         // bmp_reason
                new ParseLongEmptyAsZero(),         // bgp_error_code
                new ParseLongEmptyAsZero(),         // bgp_error_sub_code
                new ParseNullAsEmpty(),             // error_text
                new ParseLongEmptyAsZero(),         // isL3VPN
                new ParseLongEmptyAsZero(),         // isPrePolicy
                new ParseLongEmptyAsZero()          // isIPv4
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
        String [] stmt = { " REPLACE INTO bgp_peers (hash_id,router_hash_id,peer_rd,isIPv4,peer_addr,name,peer_bgp_id," +
                           "peer_as,state,isL3VPNpeer,timestamp,isPrePolicy,local_ip,local_bgp_id,local_port," +
                           "local_hold_time,local_asn,remote_port,remote_hold_time,sent_capabilities," +
                           "recv_capabilities,bmp_reason,bgp_err_code,bgp_err_subcode,error_text) VALUES ",

                           "" };
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
            sb.append("'" + rowMap.get(i).get("router_hash") + "',");
            sb.append("'" + rowMap.get(i).get("peer_rd") + "',");
            sb.append(rowMap.get(i).get("isIPv4") + ",");
            sb.append("'" + rowMap.get(i).get("remote_ip") + "',");
            sb.append("'" + rowMap.get(i).get("name") + "',");
            sb.append("'" + rowMap.get(i).get("remote_bgp_id") + "',");
            sb.append(rowMap.get(i).get("remote_asn") + ",");

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("up") ? 1 : 0) + ",");

            sb.append(rowMap.get(i).get("isL3VPN") + ",");
            sb.append("'" + rowMap.get(i).get("timestamp") + "',");
            sb.append(rowMap.get(i).get("isPrePolicy") + ",");
            sb.append("'" + rowMap.get(i).get("local_ip") + "',");
            sb.append("'" + rowMap.get(i).get("local_bgp_id") + "',");
            sb.append(rowMap.get(i).get("local_port") + ",");
            sb.append(rowMap.get(i).get("adv_holddown") + ",");
            sb.append(rowMap.get(i).get("local_asn") + ",");
            sb.append(rowMap.get(i).get("remote_port") + ",");
            sb.append(rowMap.get(i).get("remote_holddown") + ",");
            sb.append("'" + rowMap.get(i).get("adv_cap") + "',");
            sb.append("'" + rowMap.get(i).get("recv_cap") + "',");
            sb.append(rowMap.get(i).get("bmp_reason") + ",");
            sb.append(rowMap.get(i).get("bgp_error_code") + ",");
            sb.append(rowMap.get(i).get("bgp_error_sub_code") + ",");
            sb.append("'" + rowMap.get(i).get("error_text") + "'");

            sb.append(')');
        }

        return sb.toString();
    }


    /**
     * Generate MySQL RIB update statement to withdraw all rib entries
     *
     * Upon peer up or down, withdraw all RIB entries.  When the PEER is up all
     *   RIB entries will get updated.  Depending on how long the peer was down, some
     *   entries may not be present anymore, thus they are withdrawn.
     *
     * @return Multi statement update is returned, such as update ...; update ...;
     */
    public String genRibPeerUpdate() {

        StringBuilder sb = new StringBuilder();

        sb.append("SET @TRIGGER_DISABLED=TRUE; ");

        for (int i=0; i < rowMap.size(); i++) {

            if (i > 0)
                sb.append(';');

            sb.append("UPDATE rib SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(rowMap.get(i).get("hash"));
            sb.append("' AND isWithdrawn = False");

            sb.append("; UPDATE ls_nodes SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(rowMap.get(i).get("hash"));
            sb.append("' AND isWithdrawn = False");
            sb.append("; UPDATE ls_links SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(rowMap.get(i).get("hash"));
            sb.append("' AND isWithdrawn = False");
            sb.append("; UPDATE ls_prefixes SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(rowMap.get(i).get("hash"));
            sb.append("' AND isWithdrawn = False");

        }

        sb.append("SET @TRIGGER_DISABLED=FALSE; ");

        return sb.toString();
    }
}
