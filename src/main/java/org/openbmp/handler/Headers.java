package org.openbmp.handler;

import org.openbmp.helpers.IpAddr;
import org.openbmp.processor.ParseLongEmptyAsZero;
import org.openbmp.processor.ParseNullAsEmpty;
import org.openbmp.processor.ParseTimestamp;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;

/**
 * Created by ALEX on 12/2/15.
 */
public class Headers {

    private String version;

    private String collector_hash_id;

    private long length;

    private long records;

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
        this.version = headers[0].split(":")[1].trim();
        this.collector_hash_id = headers[1].split(":")[1].trim();
        this.length = Long.valueOf(headers[2].split(":")[1].trim());
        this.records = Long.valueOf(headers[3].split(":")[1].trim());
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
}
