package org.openbmp.handler;

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */

/**
 * Used to include headers of the messages
 *
 * Created by ALEX on 12/2/15.
 */
public class Headers {

    private String version;

    private String collector_hash_id;

    private long length;

    private long records;

    private String router_hash_id;

    /**
     * Handle the headers by parsing it and storing the data in memory.
     *
     * @param data
     */
    public Headers(String data) {
        parse(data);
    }

    /**
     * Setting the headers' values
     *
     * @param data
     */
    private void parse(String data) {
        String headers[] = data.split("\n");
        for(String header : headers){
            
            String value = header.split(":")[1].trim();

            switch(header.split(":")[0].trim()){
                case "V":{
                    this.version = value;
                    break;
                }
                case "C_HASH_ID":{
                    this.collector_hash_id = value;
                    break;
                }
                case "L":{
                    this.length = Long.valueOf(value);
                    break;
                }
                case "R":{
                    this.records = Long.valueOf(value);
                    break;
                }
                case "R_HASH_ID":{
                    this.router_hash_id = value;
                    break;
                }
            }
            
        }
    }

    public String getVersion() {
        return version;
    }

    public String getCollector_hash_id() {
        return collector_hash_id;
    }

    public long getLength() {
        return length;
    }

    public long getRecords() {
        return records;
    }
    public String getRouter_hash_id() {
        return router_hash_id;
    }

}
