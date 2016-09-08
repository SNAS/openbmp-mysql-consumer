package org.openbmp.mysqlquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openbmp.api.parsed.message.MsgBusFields;
import org.openbmp.api.parsed.message.Message;

public class RouterQuery extends Query{
	
	
	private Message message; 
	
	public RouterQuery(Message message, List<Map<String, Object>> rowMap){
		
		this.rowMap = rowMap;
		this.message = message;
	}
	
    /**
     * Generate MySQL insert/update statement, sans the values
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genInsertStatement() {
        String [] stmt = { " INSERT INTO routers (hash_id,name,ip_address,timestamp,isConnected,term_reason_code," +
                                  "term_reason_text,term_data,init_data,description,collector_hash_id,bgp_id) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp),isConnected=values(isConnected)," +
                                   "name=if(isConnected = 1, values(name), name)," +
                                   "bgp_id=values(bgp_id)," +
                                   "description=values(description),init_data=values(init_data)," +
                                   "term_reason_code=values(term_reason_code),term_reason_text=values(term_reason_text)," +
                                   "collector_hash_id=values(collector_hash_id)" };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert.
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genValuesStatement() {
    	
    	//DefaultColumnValues.getDefaultValue("hash");
        StringBuilder sb = new StringBuilder();

        for (int i=0; i < rowMap.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('(');
            sb.append("'" + lookupValue(MsgBusFields.HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.NAME, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.IP_ADDRESS, i)  + "',");
            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i)  + "',");

            sb.append((((String)rowMap.get(i).get("action")).equalsIgnoreCase("term") ? 0 : 1) + ",");

            sb.append(  lookupValue(MsgBusFields.TERM_CODE, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.TERM_REASON, i)  + "',");
            sb.append("'" + lookupValue(MsgBusFields.TERM_DATA, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.INIT_DATA, i)+ "',");
            sb.append("'" + lookupValue(MsgBusFields.DESCRIPTION, i) + "',");
            sb.append("'" + message.getCollector_hash_id() + "',");
            sb.append("'" + lookupValue(MsgBusFields.BGP_ID, i)  + "'");
            sb.append(')');
        }

        return sb.toString();
    }

    
    
    

    /**
     * Generate MySQL update statement to update peer status
     *
     * Avoids faulty report of peer status when router gets disconnected
     *
     * @param routerConMap         Hash of collectors/routers and connection counts
     *
     * @return Multi statement update is returned, such as update ...; update ...;
     */
    public String genPeerRouterUpdate( Map<String,Map<String, Integer>> routerConMap) {

        StringBuilder sb = new StringBuilder();

        List<Map<String, Object>> resultMap = new ArrayList<>();
        resultMap.addAll(rowMap);

        // Add the collector entry if router is seen before collector message
        if (! routerConMap.containsKey(message.getCollector_hash_id())) {
            routerConMap.put(message.getCollector_hash_id(), new ConcurrentHashMap<String, Integer>());
        }

        Map<String, Integer> routerMap = routerConMap.get(message.getCollector_hash_id());

        for (int i = 0; i < rowMap.size(); i++) {

            if (((String) lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("first") || ((String) lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("init")) {
                if (sb.length() > 0)
                    sb.append(";");

                // Upon initial router message, we set the state of all peers to down since we will get peer UP's
                sb.append("UPDATE bgp_peers SET state = 0 WHERE router_hash_id = '");
                sb.append(lookupValue(MsgBusFields.HASH, i) + "'");

                // Collector changed/heartbeat messages maintain the routerMap, but timing might prevent an update
                //    so add the router if it doesn't exist already
                if (! routerMap.containsKey((String)lookupValue(MsgBusFields.IP_ADDRESS, i)) ) {
                    routerMap.put((String)lookupValue(MsgBusFields.IP_ADDRESS, i), 1);

                } else {
                    // Increment the entry for the new connection
                    routerMap.put((String)lookupValue(MsgBusFields.IP_ADDRESS, i),
                            routerMap.get((String)lookupValue(MsgBusFields.IP_ADDRESS, i)) + 1 );
                }

            }

            else if (((String) lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("term")) {
                // Update the router map to reflect the termination
                if (routerMap.containsKey((String)lookupValue(MsgBusFields.IP_ADDRESS, i)) ) {

                    // decrement connection count or remove the router entry on term
                    if (routerMap.get((String)lookupValue(MsgBusFields.IP_ADDRESS, i)) > 1) {
                        routerMap.put((String)lookupValue(MsgBusFields.IP_ADDRESS, i),
                                routerMap.get((String)lookupValue(MsgBusFields.IP_ADDRESS, i)) - 1 );

                        // Suppress the term message since another connection exists
                        resultMap.remove(rowMap.get(i));

                    } else {
                        routerMap.remove((String)lookupValue(MsgBusFields.IP_ADDRESS, i));
                    }
                }
            }
        }

        rowMap = resultMap;

        return sb.toString();
    }

}
