package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.HeaderDefault;



public class LsLinkQuery extends Query{
	
	private List<Map<String, Object>> rowMap;
	
	public LsLinkQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " REPLACE  INTO ls_links (hash_id,peer_hash_id, path_attr_hash_id,id,mt_id,interface_addr," +
                           "neighbor_addr,isIPv4,protocol,local_link_id,remote_link_id,local_node_hash_id,remote_node_hash_id," +
                           "admin_group,max_link_bw,max_resv_bw,unreserved_bw,te_def_metric,protection_type,mpls_proto_mask," +
                           "igp_metric,srlg,name,isWithdrawn,timestamp,local_igp_router_id,local_router_id," +
                           "local_asn,remote_igp_router_id,remote_router_id,remote_asn,peer_node_sid) VALUES ",

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
            sb.append("0x" + lookupValue(HeaderDefault.mt_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.intf_ip, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.nei_ip, i) + "',");


            if (IpAddr.isIPv4(lookupValue(HeaderDefault.intf_ip, i).toString()) == true)
                sb.append("1,");
            else
                sb.append("0,");

            sb.append("'" + lookupValue(HeaderDefault.protocol, i) + "',");
            sb.append(lookupValue(HeaderDefault.local_link_id, i) + ",");
            sb.append(lookupValue(HeaderDefault.remote_link_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.local_node_hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.remote_node_hash, i) + "',");
            sb.append(lookupValue(HeaderDefault.admin_group, i) + ",");
            sb.append(lookupValue(HeaderDefault.max_link_bw, i) + ",");
            sb.append(lookupValue(HeaderDefault.max_resv_bw, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.unresv_bw, i) + "',");
            sb.append(lookupValue(HeaderDefault.te_default_metric, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.link_protection, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.mpls_proto_mask, i) + "',");
            sb.append(lookupValue(HeaderDefault.igp_metric, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.srlg, i) + "',");

            // Resolve IP address
            String hostname = "";
            if (rowMap.get(i).get("protocol").toString().contains("OSPF")) {
                hostname = IpAddr.resolveIp(lookupValue(HeaderDefault.intf_ip, i).toString());

                if (lookupValue(HeaderDefault.link_name, i).toString().length() <= 0)
                    sb.append("'" + hostname + "',");
                else
                    sb.append("'" + lookupValue(HeaderDefault.link_name, i) + "',");

            }
            else {
                sb.append("'" + lookupValue(HeaderDefault.link_name, i) + "',");
            }

            sb.append((((String)lookupValue(HeaderDefault.action, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.igp_router_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.router_id, i) + "',");
            sb.append(lookupValue(HeaderDefault.local_node_asn, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.remote_igp_router_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.remote_router_id, i) + "',");
            sb.append(lookupValue(HeaderDefault.remote_node_asn, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.peer_node_sid, i) + "'");

            sb.append(')');
        }

        return sb.toString();
    }

}
