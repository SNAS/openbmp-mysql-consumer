package org.openbmp.mysqlquery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openbmp.api.parsed.message.MsgBusFields;


/**
 * abstract class to define methods that will contain SQL query for each Object. 
 * @author mmaredia
 *
 */
public abstract class Query {
	
	
	protected List<Map<String, Object>> rowMap;
	
	/**
	 * lookup value in the rowMap for a header, return the default if absent. 
	 * @param header
	 * @param index
	 * @return Object
	 */
	protected Object lookupValue(MsgBusFields header, int index){
    	
    	if(rowMap==null || rowMap.get(index)==null)
    		return header.getDefaultValue();
    	
		Object value = rowMap.get(index).get(header.getName());
    	
    	return value==null ? header.getDefaultValue() : value;
    	
    }
	
	
    /**
     * Generate MySQL insert/update statement, sans the values
     *
     * @return Two strings are returned
     *      0 = Insert statement string up to VALUES keyword
     *      1 = ON DUPLICATE ...  or empty if not used.
     */
    public abstract String[] genInsertStatement();

    /**
     * Generate bulk values statement for SQL bulk insert.
     *
     * @return String in the format of (col1, col2, ...)[,...]
     */
    public abstract String genValuesStatement();
	

}
