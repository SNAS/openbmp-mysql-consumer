package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openbmp.api.parsed.message.HeaderDefault;

public class CollectorQuery extends Query{
	
	private List<Map<String, Object>> rowMap;
	
	public CollectorQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " INSERT INTO collectors (hash_id,state,admin_id,routers,router_count,timestamp) VALUES ",
                           " ON DUPLICATE KEY UPDATE state=values(state),timestamp=values(timestamp),routers=values(routers),router_count=values(router_count)" };
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
            sb.append((((String)lookupValue(HeaderDefault.action, i)).equalsIgnoreCase("stopped") ? "'down'" : "'up'") + ",");
            sb.append("'" + lookupValue(HeaderDefault.admin_id, i) + "',");
            sb.append("'" + lookupValue(HeaderDefault.routers, i) + "',");
            sb.append(lookupValue(HeaderDefault.router_count, i) + ",");
            sb.append("'" + lookupValue(HeaderDefault.timestamp, i) + "'");
            sb.append(')');
        }

        return sb.toString();
    }


    /**
     * Generate MySQL update statement to update router status
     *
     * Avoids faulty report of router status when collector gets disconnected
     *
     * @param routerConMap         Hash of collectors/routers and connection counts
     *
     * @return Multi statement update is returned, such as update ...; update ...;
     */
    public String genRouterCollectorUpdate( Map<String,Map<String, Integer>> routerConMap) {
        Boolean changed = Boolean.FALSE;
        StringBuilder sb = new StringBuilder();
        StringBuilder router_sql_in_list = new StringBuilder();
        router_sql_in_list.append("(");

        for (int i = 0; i < rowMap.size(); i++) {

            String action = (String) lookupValue(HeaderDefault.action, i);

            if (i > 0 && sb.length() > 0)
                sb.append(';');

            if (action.equalsIgnoreCase("started") || action.equalsIgnoreCase("stopped")) {
                sb.append("UPDATE routers SET isConnected = False WHERE collector_hash_id = '");
                sb.append(lookupValue(HeaderDefault.hash, i) + "'");

                // Collector start/stopped should always have an empty router set
                routerConMap.remove((String)lookupValue(HeaderDefault.hash, i));

            }
            else { // heartbeat or changed

                // Add concurrent connection map for collector if it does not exist already
                if (! routerConMap.containsKey((String)lookupValue(HeaderDefault.hash, i))) {
                    routerConMap.put((String)lookupValue(HeaderDefault.hash, i), new ConcurrentHashMap<String, Integer>());
                    changed = Boolean.TRUE;
                }

                String[] routerArray = ((String) lookupValue(HeaderDefault.routers, i)).split("[ ]*,[ ]*");

                if (routerArray.length > 0) {
                    // Update the router list
                    Map<String, Integer> routerMap = routerConMap.get((String) lookupValue(HeaderDefault.hash, i));
                    routerMap.clear();

                    for (String router : routerArray) {

                        if (routerMap.containsKey(router)) {                    // Increment
                            routerMap.put(router, routerMap.get(router) + 1);
                        } else {                                                // new
                            if (routerMap.size() > 0) {
                                router_sql_in_list.append(" OR ");
                            }

                            router_sql_in_list.append(" ip_address = '");
                            router_sql_in_list.append(router);
                            router_sql_in_list.append("'");

                            routerMap.put(router, 1);
                        }
                    }

                    router_sql_in_list.append(")");

                    // Update routers if there's a change
                    if (changed && router_sql_in_list.length() > 2) {
                        if (sb.length() > 0) {
                            sb.append(";");
                        }

                        sb.append("UPDATE routers SET isConnected = True WHERE collector_hash_id = '" + lookupValue(HeaderDefault.hash, i) + "' AND " + router_sql_in_list);
                    }
                }
            }
        }

        return sb.toString();
    }


}
