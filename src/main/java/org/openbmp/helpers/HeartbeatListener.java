package org.openbmp.helpers;

import org.openbmp.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

/**
 * Created by ALEX on 1/27/16.
 */
public class HeartbeatListener extends TimerTask {

    private String collectorHash;
    private BlockingQueue<Map<String, String>> writerQueue;

    public HeartbeatListener(String collectorHash, BlockingQueue<Map<String, String>> writerQueue) {
        this.collectorHash = collectorHash;
        this.writerQueue = writerQueue;
    }

    @Override
    public void run() {
        System.out.println("Dead Collector: " + collectorHash);
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE routers SET isConnected = False WHERE collector_hash_id = '" + collectorHash + "'");
        Map<String, String> update = new HashMap<>();
        update.put("query", sb.toString());
        try {
            writerQueue.put(update);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
