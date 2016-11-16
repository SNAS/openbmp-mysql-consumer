package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.MsgBusFields;



public class LsNodeQuery extends Query{
	
	public LsNodeQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " REPLACE  INTO ls_nodes (hash_id,peer_hash_id, path_attr_hash_id,asn,id,bgp_ls_id,igp_router_id," +
                           "ospf_area_id,protocol,router_id,isis_area_id,flags,name,mt_ids,isWithdrawn,timestamp," +
                            "sr_capabilities) VALUES ",

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
            sb.append("'" + lookupValue(MsgBusFields.HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.PEER_HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.BASE_ATTR_HASH, i) + "',");
            sb.append(lookupValue(MsgBusFields.PEER_ASN, i) + ",");
            sb.append(lookupValue(MsgBusFields.ROUTING_ID, i) + ",");
            sb.append("0x" + lookupValue(MsgBusFields.LS_ID, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.IGP_ROUTER_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.OSPF_AREA_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.PROTOCOL, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.ROUTER_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.ISIS_AREA_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.FLAGS, i) + "',");

            // Resolve IP address
            String hostname = "";
            if (lookupValue(MsgBusFields.PROTOCOL, i).toString().contains("OSPF"))
                hostname = IpAddr.resolveIp(lookupValue(MsgBusFields.IGP_ROUTER_ID, i).toString());
            else
                hostname = IpAddr.resolveIp(lookupValue(MsgBusFields.ROUTER_ID, i).toString());

            if (lookupValue(MsgBusFields.NAME, i).toString().length() <= 0)
                sb.append("'" + hostname + "',");
            else
                sb.append("'" + lookupValue(MsgBusFields.NAME, i) + "',");

            sb.append("'" + lookupValue(MsgBusFields.MT_ID, i) + "',");

            sb.append((((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.LS_SR_CAPABILITIES, i) + "'");

            sb.append(')');
        }

        return sb.toString();
    }


}
