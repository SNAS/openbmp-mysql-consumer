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
 * Processor class to return null/empty Long value as zero, otherwise the long value
 */
public class ParseLongEmptyAsZero extends CellProcessorAdaptor {

    public ParseLongEmptyAsZero() {
        super();
    }

    public ParseLongEmptyAsZero(CellProcessor next) {
        super(next);
    }

    public Object execute(Object value, CsvContext context) {
        Long lvalue = 0L;
        if (value == null) {
            return next.execute(lvalue, context);
        }
        else {

            try {
                lvalue = Long.valueOf(value.toString());
            } catch (NumberFormatException e) {
                // Ignore
            }
            return next.execute(lvalue, context);
        }
    }
}
