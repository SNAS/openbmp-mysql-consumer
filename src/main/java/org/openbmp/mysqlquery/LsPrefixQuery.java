package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.HeaderDefault;



public class LsPrefixQuery extends Query{
	
	
	
	private List<Map<String, Object>> rowMap;
	
	public LsPrefixQuery(List<Map<String, Object>> rowMap){
		
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
            sb.append("'" + lookupValue(HeaderDefault.hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.peer_hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.base_attr_hash, i) + "',");
            sb.append(lookupValue(HeaderDefault.routing_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.local_node_hash, i) + "',");
            sb.append("0x" + lookupValue(HeaderDefault.mt_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.protocol, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.prefix, i) + "',");
            sb.append(lookupValue(HeaderDefault.prefix_len, i) + ",");

            sb.append("X'" + IpAddr.getIpHex((String) lookupValue(HeaderDefault.prefix, i)) + "',");
            sb.append("X'" + IpAddr.getIpBroadcastHex((String)lookupValue(HeaderDefault.prefix, i), (Integer) lookupValue(HeaderDefault.prefix_len, i)) + "',");

            sb.append("'" + lookupValue(HeaderDefault.ospf_route_type, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.igp_flags, i) + "',");

            if (IpAddr.isIPv4(lookupValue(HeaderDefault.prefix, i).toString()) == true)
                sb.append("1,");
            else
                sb.append("0,");

            sb.append(lookupValue(HeaderDefault.router_tag, i) + ",");
            sb.append(lookupValue(HeaderDefault.ext_router_tag, i) + ",");
            sb.append(lookupValue(HeaderDefault.igp_metric, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.ospf_fwd_addr, i) + "',");

            sb.append((((String)lookupValue(HeaderDefault.action, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "'");

            sb.append(')');
        }

        return sb.toString();
    }


}
