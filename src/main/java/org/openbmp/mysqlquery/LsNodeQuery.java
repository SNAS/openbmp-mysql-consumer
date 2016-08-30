package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.HeaderDefault;



public class LsNodeQuery extends Query{
	
	private List<Map<String, Object>> rowMap;
	
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
            sb.append("'" + lookupValue(HeaderDefault.hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.peer_hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.base_attr_hash, i) + "',");
            sb.append(lookupValue(HeaderDefault.peer_asn, i) + ",");
            sb.append(lookupValue(HeaderDefault.routing_id, i) + ",");
            sb.append(lookupValue(HeaderDefault.ls_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.igp_router_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.ospf_area_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.protocol, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.router_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.isis_area_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.flags, i) + "',");

            // Resolve IP address
            String hostname = "";
            if (lookupValue(HeaderDefault.protocol, i).toString().contains("OSPF"))
                hostname = IpAddr.resolveIp(lookupValue(HeaderDefault.igp_router_id, i).toString());
            else
                hostname = IpAddr.resolveIp(lookupValue(HeaderDefault.router_id, i).toString());

            if (lookupValue(HeaderDefault.name, i).toString().length() <= 0)
                sb.append("'" + hostname + "',");
            else
                sb.append("'" + lookupValue(HeaderDefault.name, i) + "',");

            sb.append("'" + lookupValue(HeaderDefault.mt_id, i) + "',");

            sb.append((((String)lookupValue(HeaderDefault.action, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "'");

            sb.append(')');
        }

        return sb.toString();
    }


}
