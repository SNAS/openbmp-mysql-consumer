package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.HeaderDefault;



public class UnicastPrefixQuery extends Query{
	
	private List<Map<String, Object>> rowMap;
	
	public UnicastPrefixQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " INSERT IGNORE INTO rib (hash_id,peer_hash_id,path_attr_hash_id,isIPv4," +
                           "origin_as,prefix,prefix_len,prefix_bin,prefix_bcast_bin,prefix_bits,timestamp," +
                           "isWithdrawn,path_id,labels,isPrePolicy,isAdjRibIn) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp)," +
                               "prefix_bits=values(prefix_bits)," +
                               "path_attr_hash_id=if(values(isWithdrawn) = 1, path_attr_hash_id, values(path_attr_hash_id))," +
                               "origin_as=if(values(isWithdrawn) = 1, origin_as, values(origin_as)),isWithdrawn=values(isWithdrawn)," +
                               "path_id=values(path_id), labels=values(labels)," +
                               "isPrePolicy=values(isPrePolicy), isAdjRibIn=values(isAdjRibIn) "
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
            sb.append("'" + lookupValue(HeaderDefault.hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.peer_hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.base_attr_hash, i) + "',");
            sb.append(lookupValue(HeaderDefault.isIPv4, i) + ",");
            sb.append(lookupValue(HeaderDefault.origin_as, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.prefix, i) + "',");
            sb.append(lookupValue(HeaderDefault.prefix_len, i) + ",");

            sb.append("X'" + IpAddr.getIpHex((String) lookupValue(HeaderDefault.prefix, i)) + "',");
            sb.append("X'" + IpAddr.getIpBroadcastHex((String) lookupValue(HeaderDefault.prefix, i), (Integer) lookupValue(HeaderDefault.prefix_len, i)) + "',");
            sb.append("'" + IpAddr.getIpBits((String) lookupValue(HeaderDefault.prefix, i)).substring(0,(Integer)lookupValue(HeaderDefault.prefix_len, i)) + "',");

            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "',");
            sb.append((((String)lookupValue(HeaderDefault.action, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append(lookupValue(HeaderDefault.path_id, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.labels, i) + "',");
            sb.append(lookupValue(HeaderDefault.isPrePolicy, i) + ",");
            sb.append(lookupValue(HeaderDefault.isAdjRibIn, i));

            sb.append(')');
        }

        return sb.toString();
    }

}
