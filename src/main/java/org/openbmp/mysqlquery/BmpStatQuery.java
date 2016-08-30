package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.parsed.message.HeaderDefault;

public class BmpStatQuery extends Query{
	
	private List<Map<String, Object>> rowMap;
	
	public BmpStatQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " INSERT IGNORE INTO stat_reports (peer_hash_id,timestamp,prefixes_rejected,known_dup_prefixes,known_dup_withdraws," +
                           "updates_invalid_by_cluster_list,updates_invalid_by_as_path_loop,updates_invalid_by_originagtor_id," +
                           "updates_invalid_by_as_confed_loop,num_routes_adj_rib_in,num_routes_local_rib) VALUES ",

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
            sb.append("'" + lookupValue(HeaderDefault.peer_hash, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "',");
            sb.append(lookupValue(HeaderDefault.rejected, i) + ",");
            sb.append(lookupValue(HeaderDefault.known_dup_updates, i) + ",");
            sb.append(lookupValue(HeaderDefault.known_dup_withdraws, i) + ",");
            sb.append(lookupValue(HeaderDefault.invalid_cluster_list, i) + ",");
            sb.append(lookupValue(HeaderDefault.invalid_as_path, i) + ",");
            sb.append(lookupValue(HeaderDefault.invalid_originator, i) + ",");
            sb.append(lookupValue(HeaderDefault.invalid_as_confed, i) + ",");
            sb.append(lookupValue(HeaderDefault.pre_policy, i) + ",");
            sb.append(lookupValue(HeaderDefault.post_policy, i) + "");

            sb.append(')');
        }

        return sb.toString();
    }


}
