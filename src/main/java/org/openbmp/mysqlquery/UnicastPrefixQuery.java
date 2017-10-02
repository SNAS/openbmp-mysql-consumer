package org.openbmp.mysqlquery;

import java.util.List;
import java.util.Map;

import org.openbmp.api.helpers.IpAddr;
import org.openbmp.api.parsed.message.MsgBusFields;



public class UnicastPrefixQuery extends Query{
	
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
            sb.append("'" + lookupValue(MsgBusFields.HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.PEER_HASH, i) + "',");
            sb.append("'" + lookupValue(MsgBusFields.BASE_ATTR_HASH, i) + "',");
            sb.append(lookupValue(MsgBusFields.IS_IPV4, i) + ",");
            sb.append(lookupValue(MsgBusFields.ORIGIN_AS, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.PREFIX, i) + "',");
            sb.append(lookupValue(MsgBusFields.PREFIX_LEN, i) + ",");

            sb.append("X'" + IpAddr.getIpHex((String) lookupValue(MsgBusFields.PREFIX, i)) + "',");
            sb.append("X'" + IpAddr.getIpBroadcastHex((String) lookupValue(MsgBusFields.PREFIX, i), (Integer) lookupValue(MsgBusFields.PREFIX_LEN, i)) + "',");
            sb.append("'" + IpAddr.getIpBits((String) lookupValue(MsgBusFields.PREFIX, i)).substring(0,(Integer)lookupValue(MsgBusFields.PREFIX_LEN, i)) + "',");

            sb.append("'" + lookupValue(MsgBusFields.TIMESTAMP, i) + "',");
            sb.append((((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("del") ? 1 : 0) + ",");
            sb.append(lookupValue(MsgBusFields.PATH_ID, i) + ",");
            sb.append("'" + lookupValue(MsgBusFields.LABELS, i) + "',");
            sb.append(lookupValue(MsgBusFields.ISPREPOLICY, i) + ",");
            sb.append(lookupValue(MsgBusFields.IS_ADJ_RIB_IN, i));

            sb.append(')');
        }

        return sb.toString();
    }

    /**
     * Generate withdraw update for as_path_analysis
     *
     * \details This statement is required before any update so that it marks the old
     *      entries as withdrawn.  This also works for withdrawn prefixes.
     *
     * @return Bulk update statement to mark entries as withdrawn
     */
    public String genAsPathAnalysisWithdrawUpdate() {
        StringBuilder sb = new StringBuilder();

        sb.append("UPDATE as_path_analysis SET iswithdrawn = 1 WHERE rib_hash_id in (");

        for (int i=0; i < rowMap.size(); i++) {
            if (i > 0)
                sb.append(',');

            sb.append('"');
            sb.append(lookupValue(MsgBusFields.HASH, i));
            sb.append('"');
        }

        sb.append(") and iswithdrawn = False");

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
        String [] stmt = {" INSERT IGNORE INTO as_path_analysis (asn,asn_left,asn_right," +
                "rib_hash_id,prefix_len,prefix_bin,isIPv4,iswithdrawn)" +
                " VALUES ",
                "ON DUPLICATE KEY UPDATE timestamp=values(timestamp),iswithdrawn=values(iswithdrawn)" };
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

            if  (((String)lookupValue(MsgBusFields.ACTION, i)).equalsIgnoreCase("add")) {

                String as_path_str = ((String) lookupValue(MsgBusFields.AS_PATH, i)).trim();
                as_path_str = as_path_str.replaceAll("[{}]", "");
                String[] as_path = as_path_str.split(" ");

                //System.out.println("AS Path = " + as_path_str);

                Long left_asn = 0L;
                Long right_asn = 0L;
                Long asn = 0L;

                for (int i2 = 0; i2 < as_path.length; i2++) {
                    if (as_path[i2].length() <= 0)
                        break;

                    try {
                        asn = Long.valueOf(as_path[i2]);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        break;
                    }

                    if (asn > 0) {
                        if (i2 + 1 < as_path.length) {

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

                            sb.append('(');
                            sb.append(asn);
                            sb.append(',');
                            sb.append(left_asn);
                            sb.append(',');
                            sb.append(right_asn);
                            sb.append(",'");
                            sb.append(lookupValue(MsgBusFields.HASH, i));
                            sb.append("',");
                            sb.append(lookupValue(MsgBusFields.PREFIX_LEN, i));
                            sb.append(',');
                            sb.append("X'" + IpAddr.getIpHex((String) lookupValue(MsgBusFields.PREFIX, i)) + "',");
                            sb.append(lookupValue(MsgBusFields.IS_IPV4, i));
                            sb.append(",0)");

                        } else {
                            // No more left in path
                            if (sb.length() > 0)
                                sb.append(',');

                            sb.append('(');
                            sb.append(asn);
                            sb.append(',');
                            sb.append(left_asn);
                            sb.append(',');
                            sb.append('0'); // Right ASN is zero
                            sb.append(",'");
                            sb.append(lookupValue(MsgBusFields.HASH, i));
                            sb.append("',");
                            sb.append(lookupValue(MsgBusFields.PREFIX_LEN, i));
                            sb.append(',');
                            sb.append("X'" + IpAddr.getIpHex((String) lookupValue(MsgBusFields.PREFIX, i)) + "',");
                            sb.append(lookupValue(MsgBusFields.IS_IPV4, i));
                            sb.append(",0)");

                            break;
                        }

                        left_asn = asn;
                    }
                }
            }
        }

        //System.out.println("AS insert: " + sb.toString());
        if (sb.length() <= 0)
            sb.append("");
        return sb.toString();
    }
}
