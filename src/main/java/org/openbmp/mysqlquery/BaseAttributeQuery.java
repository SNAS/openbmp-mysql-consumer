package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.parsed.message.MsgBusFields;

public class BaseAttributeQuery extends Query{
	
	public BaseAttributeQuery(List<Map<String, Object>> rowMap){
		
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
        String [] stmt = { " INSERT INTO path_attrs (hash_id,peer_hash_id,origin,as_path,origin_as,next_hop,med,local_pref," +
                                "isAtomicAgg,aggregator,community_list,ext_community_list," +
                                "cluster_list,originator_id,as_path_count,nexthop_isIPv4,timestamp) VALUES ",

                           " ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
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
            sb.append("'" + lookupValue(MsgBusFields.ORIGIN, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.AS_PATH, i) + "',");
            sb.append(lookupValue(MsgBusFields.ORIGIN_AS, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.NEXTHOP, i) + "',");
            sb.append(lookupValue(MsgBusFields.MED, i) + ",");
            sb.append(lookupValue(MsgBusFields.LOCAL_PREF, i) + ",");
            sb.append(lookupValue(MsgBusFields.ISATOMICAGG, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.AGGREGATOR, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.COMMUNITY_LIST, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.EXT_COMMUNITY_LIST, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.CLUSTER_LIST, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.ORIGINATOR_ID, i) + "',");
            sb.append(lookupValue(MsgBusFields.AS_PATH_COUNT, i) + ",");
            sb.append(lookupValue(MsgBusFields.IS_NEXTHOP_IPV4, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "'");
            sb.append(')');
        }

        return sb.toString();
    }

    /**
     * Generate MySQL insert/update statement, sans the values for as_path_analysis
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genAsPathAnalysisStatement() {
        String [] stmt = {" INSERT IGNORE INTO as_path_analysis (asn,asn_left,asn_right,path_attr_hash_id, peer_hash_id)" +
                                    " VALUES ", "" };

                          //" ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert for as_path_analysis
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genAsPathAnalysisValuesStatement() {
        StringBuilder sb = new StringBuilder();

        /*
         * Iterate through the AS Path and extract out the left and right ASN for each AS within
         *     the AS PATH
         */
        for (int i=0; i < rowMap.size(); i++) {

            String as_path_str = ((String)lookupValue(MsgBusFields.AS_PATH, i)).trim();
            as_path_str = as_path_str.replaceAll("[{}]", "");
            String[] as_path = as_path_str.split(" ");

            //System.out.println("AS Path = " + as_path_str);

            Long left_asn = 0L;
            Long right_asn = 0L;
            Long asn = 0L;

            for (int i2=0; i2 < as_path.length; i2++) {
                if (as_path[i2].length() <= 0)
                    break;

                try {
                    asn = Long.valueOf(as_path[i2]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    break;
                }

                if (asn > 0 ) {
                    if (i2+1 < as_path.length) {

                        if (as_path[i2 + 1].length() <= 0)
                            break;

                        try {
                            right_asn = Long.valueOf(as_path[i2 + 1]);

                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            break;
                        }

                        if (right_asn.equals(asn)) {
                            continue;
                        }

                        if (sb.length() > 0)
                            sb.append(',');

                        sb.append("(" + asn + "," + left_asn + "," + right_asn + ",'" + lookupValue(MsgBusFields.HASH, i) + "','" +
                        		lookupValue(MsgBusFields.PEER_HASH, i) + "')");


                    } else {
                        // No more left in path
                        if (sb.length() > 0)
                            sb.append(',');

                        sb.append("(" + asn + "," + left_asn + ",0,'" + lookupValue(MsgBusFields.HASH, i) + "','" +
                        		lookupValue(MsgBusFields.PEER_HASH, i) + "')");
                        break;
                    }

                    left_asn = asn;
                }
            }
        }

        //System.out.println("AS insert: " + sb.toString());
        if (sb.length() <= 0)
            sb.append("");
        return sb.toString();
    }

    /**
     * Generate MySQL insert/update statement, sans the values for community_analysis
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE KEY UPDATE ...  or empty if not used.
     */
    public String[] genCommunityAnalysisStatement() {
        String [] stmt = {" INSERT IGNORE INTO community_analysis (community,part1,part2,path_attr_hash_id, peer_hash_id)" +
                " VALUES ", "" };

                //" ON DUPLICATE KEY UPDATE timestamp=values(timestamp) " };
        return stmt;
    }

    /**
     * Generate bulk values statement for SQL bulk insert for community_analysis
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public String genCommunityAnalysisValuesStatement() {
        StringBuilder sb = new StringBuilder();

        /*
         * Iterate through the community list and extract part1 and part2,
         * which are separated by colons
         */
        for (int i=0; i < rowMap.size(); i++) {
            String communityString = ((String)lookupValue(MsgBusFields.COMMUNITY_LIST, i)).trim();
            String[] communityList = communityString.split(" ");

            String path_attr_hash = (String) lookupValue(MsgBusFields.HASH, i);
            String peer_hash = (String) lookupValue(MsgBusFields.PEER_HASH, i);

            for (int j = 0; j < communityList.length; j++) {
                if (communityList[j].length() <= 0) {
                    break;
                }

                Integer part1 = null, part2 = null;
                try {
                    String[] twoParts = communityList[j].split(":");
                    part1 = Integer.valueOf(twoParts[0]);
                    part2 = Integer.valueOf(twoParts[1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    break;
                }

                if (part1 != null && part2 != null) {
                    if (sb.length() > 0)
                        sb.append(",");

                    sb.append("('" + communityList[j] + "',"
                            + part1 + ","
                            + part2 + ",'"
                            + path_attr_hash + "','"
                            + peer_hash + "')"
                    );
                }
            }
        }

        //System.out.println("AS insert: " + sb.toString());
        if (sb.length() <= 0)
            sb.append("");
        return sb.toString();
    }

}
