package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.MsgBusFields;



public class LsLinkQuery extends Query{
	
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
        String [] stmt = { " INSERT IGNORE INTO ls_links (hash_id,peer_hash_id, path_attr_hash_id,id,mt_id,interface_addr," +
                           "neighbor_addr,isIPv4,protocol,local_link_id,remote_link_id,local_node_hash_id,remote_node_hash_id," +
                           "admin_group,max_link_bw,max_resv_bw,unreserved_bw,te_def_metric,protection_type,mpls_proto_mask," +
                           "igp_metric,srlg,name,isWithdrawn,timestamp,local_igp_router_id,local_router_id," +
                           "local_asn,remote_igp_router_id,remote_router_id,remote_asn,peer_node_sid, sr_adjacency_sids) VALUES ",

                           " ON DUPLICATE KEY UPDATE " +
                                   "iswithdrawn=values(iswithdrawn)," +
                                   "path_attr_hash_id=if(values(iswithdrawn), path_attr_hash_id, values(path_attr_hash_id))," +
                                   "interface_addr=if(values(iswithdrawn), interface_addr, values(interface_addr))," +
                                   "neighbor_addr=if(values(iswithdrawn), neighbor_addr, values(neighbor_addr))," +
                                   "local_link_id=if(values(iswithdrawn), local_link_id, values(local_link_id))," +
                                   "remote_link_id=if(values(iswithdrawn), remote_link_id, values(remote_link_id))," +
                                   "admin_group=if(values(iswithdrawn), admin_group, values(admin_group))," +
                                   "max_link_bw=if(values(iswithdrawn), max_link_bw, values(max_link_bw))," +
                                   "max_resv_bw=if(values(iswithdrawn), max_resv_bw, values(max_resv_bw))," +
                                   "unreserved_bw=if(values(iswithdrawn), unreserved_bw, values(unreserved_bw))," +
                                   "te_def_metric=if(values(iswithdrawn), te_def_metric, values(te_def_metric))," +
                                   "protection_type=if(values(iswithdrawn), protection_type, values(protection_type))," +
                                   "mpls_proto_mask=if(values(iswithdrawn), mpls_proto_mask, values(mpls_proto_mask))," +
                                   "max_resv_bw=if(values(iswithdrawn), max_resv_bw, values(max_resv_bw))," +
                                   "igp_metric=if(values(iswithdrawn), igp_metric, values(igp_metric))," +
                                   "srlg=if(values(iswithdrawn), srlg, values(srlg))," +
                                   "name=if(values(iswithdrawn), name, values(name))," +
                                   "local_igp_router_id=if(values(iswithdrawn), local_igp_router_id, values(local_igp_router_id))," +
                                   "local_router_id=if(values(iswithdrawn), local_router_id, values(local_router_id))," +
                                   "local_asn=if(values(iswithdrawn), local_asn, values(local_asn))," +
                                   "remote_igp_router_id=if(values(iswithdrawn), remote_igp_router_id, values(remote_igp_router_id))," +
                                   "remote_router_id=if(values(iswithdrawn), remote_router_id, values(remote_router_id))," +
                                   "remote_asn=if(values(iswithdrawn), remote_asn, values(remote_asn))," +
                                   "peer_node_sid=if(values(iswithdrawn), peer_node_sid, values(peer_node_sid))," +
                                   "sr_adjacency_sids=if(values(iswithdrawn), sr_adjacency_sids, values(sr_adjacency_sids))"
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
            sb.append("'" + lookupValue(MsgBusFields.HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.PEER_HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.BASE_ATTR_HASH, i) + "',");
            sb.append(lookupValue(MsgBusFields.ROUTING_ID, i) + ",");
            sb.append("0x" + lookupValue(MsgBusFields.MT_ID, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.INTF_IP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.NEI_IP, i) + "',");


            if (IpAddr.isIPv4(lookupValue(MsgBusFields.INTF_IP, i).toString()) == true)
                sb.append("1,");
            else
                sb.append("0,");

            sb.append("'" + lookupValue(MsgBusFields.PROTOCOL, i) + "',");
            sb.append(lookupValue(MsgBusFields.LOCAL_LINK_ID, i) + ",");
            sb.append(lookupValue(MsgBusFields.REMOTE_LINK_ID, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.LOCAL_NODE_HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.REMOTE_NODE_HASH, i) + "',");
            sb.append(lookupValue(MsgBusFields.ADMIN_GROUP, i) + ",");
            sb.append(lookupValue(MsgBusFields.MAX_LINK_BW, i) + ",");
            sb.append(lookupValue(MsgBusFields.MAX_RESV_BW, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.UNRESV_BW, i) + "',");
            sb.append(lookupValue(MsgBusFields.TE_DEFAULT_METRIC, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.LINK_PROTECTION, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.MPLS_PROTO_MASK, i) + "',");
            sb.append(lookupValue(MsgBusFields.IGP_METRIC, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.SRLG, i) + "',");

            // Resolve IP address
            String hostname = "";
            if (lookupValue(MsgBusFields.PROTOCOL, i).toString().contains("OSPF")) {
                hostname = IpAddr.resolveIp(lookupValue(MsgBusFields.INTF_IP, i).toString());

                if (lookupValue(MsgBusFields.LINK_NAME, i).toString().length() <= 0)
                    sb.append("'" + hostname + "',");
                else
                    sb.append("'" + lookupValue(MsgBusFields.LINK_NAME, i) + "',");

            }
            else {
                sb.append("'" + lookupValue(MsgBusFields.LINK_NAME, i) + "',");
            }

            sb.append((((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.IGP_ROUTER_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.ROUTER_ID, i) + "',");
            sb.append(lookupValue(MsgBusFields.LOCAL_NODE_ASN, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.REMOTE_IGP_ROUTER_ID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.REMOTE_ROUTER_ID, i) + "',");
            sb.append(lookupValue(MsgBusFields.REMOTE_NODE_ASN, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.PEER_NODE_SID, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.LS_ADJACENCY_SID, i) + "'");

            sb.append(')');
        }

        return sb.toString();
    }

}
