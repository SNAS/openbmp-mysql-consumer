package org.openbmp.processor;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.util.CsvContext;


/**
 * Processor class to parse the timestamp from openbmp.parsed.* messages
 *
 *      Format is standard java.sql.Timestamp of 'YYYY-mm-dd HH:MM:SS.ffffff'
 */
public class ParseTimestamp extends CellProcessorAdaptor {

    public ParseTimestamp() {
        super();
    }

    public ParseTimestamp(CellProcessor next) {
        super(next);
    }

    public Object execute(Object value, CsvContext context) {
        validateInputNotNull(value, context);  // throws an Exception if the input is null

        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(value.toString());

        return next.execute(ts, context);
    }
}
