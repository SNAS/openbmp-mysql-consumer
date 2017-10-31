/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.openbmp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.cli.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration for program
 *
 * Will parse command line args and store results in class instance
 */
public class Config {
    private static final Logger logger = LogManager.getFormatterLogger(MySQLConsumerApp.class.getName());

    private static Config instance = null;

    private Options options = new Options();

    /// Config variables
    private Integer writer_max_threads_per_type = 3;             // Maximum number of writes per type
    private Integer writer_allowed_over_queue_times = 2;         // Threshold to add threads when count is above this value
    private Long writer_millis_thread_scale_back = 1200000L;     // Age in milliseconds when threads can be deleted

    private String cfg_file = null;
    private Integer expected_heartbeat_interval = 330000;
    private Integer stats_interval = 300;
    private Boolean disable_as_path_indexing = false;
    private String db_host = "localhost:3306";
    private String db_user = "openbmp";
    private String db_pw = "openbmp";
    private String db_name = "openBMP";
    private Integer db_batch_time_millis = 75;
    private Integer db_batch_records = 200;
    private Integer db_retries = 10;
    private Properties kafka_consumer_props;
    private Set<Pattern> kafka_topic_patterns;
    private Integer topic_subscribe_delay_millis = 10000;       // topic subscription interval delay


    //Turns this class to a singleton
    public static Config getInstance() {
        if(instance==null)
        {
            instance = new Config();
        }
        return instance;
    }

    protected Config() {
        options.addOption("cf", "config_file", true, "Configuration filename, default is to load the JAR/CP default one");
        options.addOption("b", "bootstrap", true, "Bootstrap servers hostanme:port (default is localhost:9092)");
        options.addOption("g", "group.id", true, "Kafka group ID (default is openbmp-mysql-consumer)");
        options.addOption("c", "client.id", true, "Kafka client ID (default uses group.id");
        options.addOption("ol", "offset_largest", false, "Set offset to largest when offset is not known");
        options.addOption("e", "expected_heartbeat_interval", true, "Max age in minutes for collector heartbeats (default is 6 minutes)");
        options.addOption("s", "stats_interval", true, "Stats interval in seconds (default 300 seconds, 0 disables");
        options.addOption("dh", "db_host", true, "Database host (default is localhost:3306)");
        options.addOption("du", "db_user", true, "Database username (default is openbmp)");
        options.addOption("dp", "db_pw", true, "Database password (default is openbmp)");
        options.addOption("dn", "db_name", true, "Database name (default is openBMP)");
        options.addOption("h", "help", false, "Usage help");


        kafka_consumer_props = new Properties();
        consumerConfigDefaults();

        kafka_topic_patterns = new LinkedHashSet<>();
    }

    /**
     * Parse command line args
     *
     * @param       args        Command line args to parse
     * @return      True if error, false if successfully parsed
     */
    public Boolean parse(String [] args) {
        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                help();
                System.exit(0);
            }

            if (cmd.hasOption("b"))
                kafka_consumer_props.setProperty("bootstrap.servers", cmd.getOptionValue("b"));

            if (cmd.hasOption("ol"))
                kafka_consumer_props.setProperty("auto.offset.reset", "latest");

            if (cmd.hasOption("g"))
                kafka_consumer_props.setProperty("group.id", cmd.getOptionValue("g"));

            if (cmd.hasOption("e"))
                expected_heartbeat_interval = Integer.valueOf(cmd.getOptionValue("e")) * 60 * 1000 + 30000;

            if (cmd.hasOption("s"))
                stats_interval = Integer.valueOf(cmd.getOptionValue("s"));

            if (cmd.hasOption("cf"))
                cfg_file = cmd.getOptionValue("cf");

            if (cmd.hasOption("c"))
                kafka_consumer_props.setProperty("client.id", cmd.getOptionValue("c"));

            if (cmd.hasOption("dh"))
                db_host = cmd.getOptionValue("dh");

            if (cmd.hasOption("du"))
                db_user = cmd.getOptionValue("du");

            if (cmd.hasOption("dp"))
                db_pw = cmd.getOptionValue("dp");

            if (cmd.hasOption("dn"))
                db_name = cmd.getOptionValue("dn");

        } catch (ParseException e) {
            //e.printStackTrace();

            System.out.println("Failed to parse commandline args: " + e.getMessage());
            help();

            return true;
        }

        return false;
    }

    public void help() {
        HelpFormatter fmt = new HelpFormatter();

        StackTraceElement[] stack = Thread.currentThread ().getStackTrace ();
        StackTraceElement main = stack[stack.length - 1];
        String mainClassName = main.getClassName ();

        fmt.printHelp(80, mainClassName, "\nOPTIONS:", options, "\n");
    }


    public boolean loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> map_cfg = null;

        TypeReference<HashMap<String, Object>> typeRef_cfg = new TypeReference<HashMap<String, Object>>() {};

        try {
            if (cfg_file == null) {
                map_cfg = mapper.readValue(new InputStreamReader(getClass().getResourceAsStream("/obmp-mysql-consumer.yml")), typeRef_cfg);

            } else {
                logger.info("Loading custom configuration file");
                map_cfg = mapper.readValue(new File(cfg_file), typeRef_cfg);
            }

            for (Map.Entry<String, Object> entry: map_cfg.entrySet()) {
                logger.debug("key: %s value: %s", entry.getKey(), entry.getValue());

                /*
                 * Base config
                 */
                if (entry.getKey().equalsIgnoreCase("base")) {
                    for (Map.Entry<String, Object> subEntry: ((Map<String, Object>) entry.getValue()).entrySet()) {
                        if (subEntry.getKey().equalsIgnoreCase("stats_interval"))
                            stats_interval = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("heartbeat_max_age"))
                            expected_heartbeat_interval = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("writer_max_threads_per_type")) {
                            writer_max_threads_per_type = Integer.valueOf(subEntry.getValue().toString());
                        }

                        else if (subEntry.getKey().equalsIgnoreCase("writer_allowed_over_queue_times"))
                            writer_allowed_over_queue_times = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("writer_seconds_thread_scale_back"))
                            writer_millis_thread_scale_back = Long.valueOf(subEntry.getValue().toString()) * 1000;

                        else if (subEntry.getKey().equalsIgnoreCase("disable_as_path_indexing"))
                            disable_as_path_indexing = Boolean.valueOf(subEntry.getValue().toString());
                    }
                }

                /*
                 * MySQL Config
                 */
                if (entry.getKey().equalsIgnoreCase("mysql")) {
                    for (Map.Entry<String, Object> subEntry : ((Map<String, Object>) entry.getValue()).entrySet()) {
                        if (subEntry.getKey().equalsIgnoreCase("host"))
                            db_host = subEntry.getValue().toString();

                        else if (subEntry.getKey().equalsIgnoreCase("db_name"))
                            db_name = subEntry.getValue().toString();

                        else if (subEntry.getKey().equalsIgnoreCase("username"))
                            db_user = subEntry.getValue().toString();

                        else if (subEntry.getKey().equalsIgnoreCase("password"))
                            db_pw = subEntry.getValue().toString();

                        else if (subEntry.getKey().equalsIgnoreCase("batch_records"))
                            db_batch_records = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("retries"))
                            db_retries = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("batch_time_millis"))
                            db_batch_time_millis = Integer.valueOf(subEntry.getValue().toString());
                    }
                }

                /*
                 * Kafka Config
                 */
                if (entry.getKey().equalsIgnoreCase("kafka")) {

                    for (Map.Entry<String, Object> subEntry : ((Map<String, Object>) entry.getValue()).entrySet()) {
                        if (subEntry.getKey().equalsIgnoreCase("topic_subscribe_delay_millis"))
                            topic_subscribe_delay_millis = Integer.valueOf(subEntry.getValue().toString());

                        else if (subEntry.getKey().equalsIgnoreCase("consumer_config")) {
                            /*
                             * Consumer Config
                             */
                            Map<String, Object> map = ((Map<String, Object>) subEntry.getValue());

                            for (Map.Entry<String, Object> cEntry : map.entrySet()) {
                                logger.debug("kafka consumer config - key: %25s value: %s", cEntry.getKey(), cEntry.getValue());
                                kafka_consumer_props.setProperty(cEntry.getKey(), cEntry.getValue().toString());
                            }

                        }

                        else if (subEntry.getKey().equalsIgnoreCase("subscribe_topic_patterns")) {
                            List<String> patterns = ((List<String>) subEntry.getValue());

                            for (String pat: patterns) {
                                logger.debug("topic pattern: %s", pat);
                                kafka_topic_patterns.add(Pattern.compile(pat));
                            }

                        }
                    }
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    /*
     * Default consumer properties
     */
    private void consumerConfigDefaults() {
        kafka_consumer_props.setProperty("key.deserializer", StringDeserializer.class.getName());
        kafka_consumer_props.setProperty("value.deserializer", StringDeserializer.class.getName());
        kafka_consumer_props.setProperty("enable.auto.commit", "true");

        kafka_consumer_props.setProperty("bootstrap.servers", "localhost:9092");
        kafka_consumer_props.setProperty("group.id", "openbmp-mysql-consumer");
        kafka_consumer_props.setProperty("client.id", "openbmp-mysql-consumer");
        kafka_consumer_props.setProperty("session.timeout.ms", "16000");
        kafka_consumer_props.setProperty("max.poll.interval.ms", "16000");
        kafka_consumer_props.setProperty("max.partition.fetch.bytes", "2000000");
        kafka_consumer_props.setProperty("heartbeat.interval.ms", "10000");
        kafka_consumer_props.setProperty("max.poll.records", "2000");
        kafka_consumer_props.setProperty("fetch.max.wait.ms", "50");
        kafka_consumer_props.setProperty("auto.offset.reset", "earliest");
    }


    String getCfg_file() {
        return cfg_file;
    }

    Integer getWriter_max_threads_per_type() {
        return writer_max_threads_per_type;
    }

    Integer getExpected_heartbeat_interval() {
        return expected_heartbeat_interval;
    }

    Integer getWriter_allowed_over_queue_times() {
        return writer_allowed_over_queue_times;
    }

    Long getWriter_millis_thread_scale_back() {
        return writer_millis_thread_scale_back;
    }

    Properties getKafka_consumer_props() {
        return kafka_consumer_props;
    }

    Set<Pattern> getKafka_topic_patterns() {
        return kafka_topic_patterns;
    }

    Integer getTopic_subscribe_delay_millis() {
        return topic_subscribe_delay_millis;
    }

    String getDbHost() { return db_host; }

    String getDbUser() { return db_user; }

    String getDbPw() { return db_pw; }

    String getDbName() { return db_name; }

    Integer getDb_batch_time_millis() {
        return db_batch_time_millis;
    }

    Integer getDb_batch_records() {
        return db_batch_records;
    }

    Integer getDb_retries() {
        return db_retries;
    }

    public Integer getHeartbeatInterval() { return expected_heartbeat_interval; }

    Boolean getDisable_as_path_indexing() {
        return disable_as_path_indexing;
    }

    Integer getStatsInterval() { return stats_interval; }
}
