package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.MsgBusFields;



public class LsPrefixQuery extends Query{
	
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
        String [] stmt = { " INSERT IGNORE INTO ls_prefixes (hash_id,peer_hash_id, path_attr_hash_id,id,local_node_hash_id," +
                           "mt_id,protocol,prefix,prefix_len,prefix_bin,prefix_bcast_bin,ospf_route_type," +
                           "igp_flags,isIPv4,route_tag,ext_route_tag,metric,ospf_fwd_addr,isWithdrawn,timestamp," +
						   "sr_prefix_sids) VALUES ",

							"  ON DUPLICATE KEY UPDATE " +
									"iswithdrawn=values(iswithdrawn)," +
									"path_attr_hash_id=if(values(iswithdrawn), path_attr_hash_id, values(path_attr_hash_id))," +
									"ospf_route_type=if(values(iswithdrawn), ospf_route_type, values(ospf_route_type))," +
									"igp_flags=if(values(iswithdrawn), igp_flags, values(igp_flags))," +
									"route_tag=if(values(iswithdrawn), route_tag, values(route_tag))," +
									"ext_route_tag=if(values(iswithdrawn), ext_route_tag, values(ext_route_tag))," +
									"ospf_fwd_addr=if(values(iswithdrawn), ospf_fwd_addr, values(ospf_fwd_addr))," +
									"metric=if(values(iswithdrawn), metric, values(metric))," +
									"sr_prefix_sids=if(values(iswithdrawn), sr_prefix_sids, values(sr_prefix_sids))"
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
				sb.append("'" + lookupValue(MsgBusFields.LOCAL_NODE_HASH, i) + "',");
				sb.append("0x" + lookupValue(MsgBusFields.MT_ID, i) + ",");
				sb.append("'" + lookupValue(MsgBusFields.PROTOCOL, i) + "',");
				sb.append("'" + lookupValue(MsgBusFields.PREFIX, i) + "',");
				sb.append(lookupValue(MsgBusFields.PREFIX_LEN, i) + ",");

				sb.append("X'" + IpAddr.getIpHex((String) lookupValue(MsgBusFields.PREFIX, i)) + "',");
				sb.append("X'" + IpAddr.getIpBroadcastHex((String)lookupValue(MsgBusFields.PREFIX, i), (Integer) lookupValue(MsgBusFields.PREFIX_LEN, i)) + "',");

				sb.append("'" + lookupValue(MsgBusFields.OSPF_ROUTE_TYPE, i) + "',");
				sb.append("'" + lookupValue(MsgBusFields.IGP_FLAGS, i) + "',");

				if (IpAddr.isIPv4(lookupValue(MsgBusFields.PREFIX, i).toString()) == true)
				    sb.append("1,");
				else
				    sb.append("0,");

				sb.append(lookupValue(MsgBusFields.ROUTE_TAG, i) + ",");
				sb.append(lookupValue(MsgBusFields.EXT_ROUTE_TAG, i) + ",");
				sb.append(lookupValue(MsgBusFields.IGP_METRIC, i) + ",");
				sb.append("'" + lookupValue(MsgBusFields.OSPF_FWD_ADDR, i) + "',");

				sb.append((((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
				sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "',");
                sb.append("'" + lookupValue(MsgBusFields.LS_PREFIX_SID, i) + "'");

				sb.append(')');
            
        }

        return sb.toString();
    }


}
