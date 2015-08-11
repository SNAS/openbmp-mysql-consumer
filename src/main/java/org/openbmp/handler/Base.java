package org.openbmp.handler;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Format class for collector parsed messages (openbmp.parsed.collector)
 *
 * Schema Version: 1
 *
 */
public abstract class Base {
    /**
     * column field header names
     *      Will be the MAP key for fields, order matters and must match TSV order of fields.
     */
    protected String [] headerNames;

    protected List<Map<String, Object>> rowMap;

    protected Base() {
        headerNames = null;
        rowMap = new ArrayList<Map<String, Object>>();
    }


    /**
     * Parse TSV rows of data from message
     *
     * @param data          TSV data (MUST not include the headers)
     *
     * @return  True if error, False if no errors
     */
    public boolean parse(String data) {

        final CellProcessor[] processors = getProcessors();
        ICsvMapReader mapReader = null;

        try {
            mapReader = new CsvMapReader(new StringReader(data), CsvPreference.TAB_PREFERENCE);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

        if (mapReader == null)
            return true;

        try {
            //System.out.println("Headers=" + Arrays.toString(headerNames));
            Map<String, Object> map;
            while( (map = mapReader.read(headerNames, processors)) != null )
            {
                rowMap.add(map);
                //System.out.println(String.format("lineNo=%s, rowNo=%s, Map=%s", mapReader.getLineNumber(),
                //        mapReader.getRowNumber(), map));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return true;

        } catch (org.supercsv.exception.SuperCsvException e) {
            e.printStackTrace();
            return true;

        } catch (Exception e){
            e.printStackTrace();
            return true;
        }

        return false;
    }

    /**
     * Processors used for each field.
     *
     * Order matters and must match the same order as defined in headerNames
     *
     * @return array of cell processors
     */
    protected abstract CellProcessor[] getProcessors();

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
