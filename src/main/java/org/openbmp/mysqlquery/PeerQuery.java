package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.parsed.message.MsgBusFields;

public class PeerQuery extends Query{
	
	public PeerQuery(List<Map<String, Object>> rowMap){
		
		this.rowMap = rowMap;
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
            sb.append("'" + lookupValue(MsgBusFields.HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.ROUTER_HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.PEER_RD, i) + "',");
            sb.append(lookupValue(MsgBusFields.IS_IPV4, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.REMOTE_IP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.NAME, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.REMOTE_BGP_ID, i) + "',");
            sb.append(lookupValue(MsgBusFields.REMOTE_ASN, i) + ",");

            sb.append((((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("up") ? 1 : 0) + ",");

            sb.append(lookupValue(MsgBusFields.IS_L3VPN, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "',");
            sb.append(lookupValue(MsgBusFields.ISPREPOLICY, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.LOCAL_IP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.LOCAL_BGP_ID, i) + "',");
            sb.append(lookupValue(MsgBusFields.LOCAL_PORT, i) + ",");
            sb.append(lookupValue(MsgBusFields.ADV_HOLDDOWN, i) + ",");
            sb.append(lookupValue(MsgBusFields.LOCAL_ASN, i) + ",");
            sb.append(lookupValue(MsgBusFields.REMOTE_PORT, i) + ",");
            sb.append(lookupValue(MsgBusFields.REMOTE_HOLDDOWN, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.ADV_CAP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.RECV_CAP, i) + "',");
            sb.append(lookupValue(MsgBusFields.BMP_REASON, i) + ",");
            sb.append(lookupValue(MsgBusFields.BGP_ERROR_CODE, i) + ",");
            sb.append(lookupValue(MsgBusFields.BGP_ERROR_SUB_CODE, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.ERROR_TEXT, i) + "'");

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
            sb.append(lookupValue(MsgBusFields.HASH, i));
            sb.append("' AND isWithdrawn = False");

            sb.append("; UPDATE ls_nodes SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(lookupValue(MsgBusFields.HASH, i));
            sb.append("' AND isWithdrawn = False");
            sb.append("; UPDATE ls_links SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(lookupValue(MsgBusFields.HASH, i));
            sb.append("' AND isWithdrawn = False");
            sb.append("; UPDATE ls_prefixes SET isWithdrawn = True WHERE peer_hash_id = '");
            sb.append(lookupValue(MsgBusFields.HASH, i));
            sb.append("' AND isWithdrawn = False");

        }

        sb.append("; SET @TRIGGER_DISABLED=FALSE; ");

        return sb.toString();
    }

}
