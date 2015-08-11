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
 * Processor class to return empty string if null or the string value if not null
 */
public class ParseNullAsEmpty extends CellProcessorAdaptor {

    public ParseNullAsEmpty() {
        super();
    }

    public ParseNullAsEmpty(CellProcessor next) {
        super(next);
    }

    public Object execute(Object value, CsvContext context) {
        if (value == null)
            return next.execute("", context);
        else
            return next.execute(value.toString(), context);
    }
}
