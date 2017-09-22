#!/usr/bin/env python
"""
  Copyright (c) 2013-2014 Cisco Systems, Inc. and others.  All rights reserved.

  This program and the accompanying materials are made available under the
  terms of the Eclipse Public License v1.0 which accompanies this distribution,
  and is available at http://www.eclipse.org/legal/epl-v10.html

  .. moduleauthor:: Tim Evens <tievens@cisco.com>
"""
import mysql.connector as mysql
import subprocess
from multiprocessing.pool import ThreadPool,AsyncResult
import os
import sys
from time import time,sleep
from datetime import datetime

#: Interval Timestamp
#:    Multiple queries are used to update the same object for an interval run,
#:    therefore all the updates should have the timestamp of when this script started
INTERVAL_TIMESTAMP = time()

#: Max number of origin ASN's to query in a single select statement
MAX_BATCH_SELECT_ORIGIN = 10

#: Max number of transit ASN's to query in a single select statement
MAX_BATCH_SELECT_TRANSIT = 10

#: Record dictionary
ORIGIN_RECORD_DICT = {}
RECORD_DICT = {}

# ----------------------------------------------------------------
# Tables schema
# ----------------------------------------------------------------

#: Insert trigger for gen_asn_stats"
TRIGGER_INSERT_STATUS_NAME = "ins_gen_asn_stats"
TRIGGER_CREATE_INSERT_STATUS_DEF = (
#        "delimiter //\n"
        "CREATE TRIGGER " + TRIGGER_INSERT_STATUS_NAME + " BEFORE INSERT ON gen_asn_stats\n"
        "FOR EACH ROW\n"
        "    BEGIN\n"
        "        declare last_ts timestamp;\n"
        "        declare v4_o_count bigint(20) unsigned default 0;\n"
        "        declare v6_o_count bigint(20) unsigned default 0;\n"
        "        declare v4_t_count bigint(20) unsigned default 0;\n"
        "        declare v6_t_count bigint(20) unsigned default 0;\n"

        "        SET sql_mode = '';\n"

        "        SELECT transit_v4_prefixes,transit_v6_prefixes,origin_v4_prefixes,\n"
        "                    origin_v6_prefixes,timestamp\n"
        "            INTO v4_t_count,v6_t_count,v4_o_count,v6_o_count,last_ts\n"
        "            FROM gen_asn_stats WHERE asn = new.asn \n"
        "            ORDER BY timestamp DESC limit 1;\n"

        "        IF (new.transit_v4_prefixes = v4_t_count AND new.transit_v6_prefixes = v6_t_count\n"
        "                AND new.origin_v4_prefixes = v4_o_count AND new.origin_v6_prefixes = v6_o_count) THEN\n"

                    # everything is the same, cause the insert to fail (duplicate)
        "            set new.timestamp = last_ts;\n"
        "        ELSE\n"
        
        "    IF (v4_t_count > 0 AND new.transit_v4_prefixes > 0 AND new.transit_v4_prefixes != v4_t_count)  THEN\n"
        "      SET new.transit_v4_change = cast(if(new.transit_v4_prefixes > v4_t_count,\n"
        "                                   new.transit_v4_prefixes / v4_t_count,\n"
        "                                   v4_t_count / new.transit_v4_prefixes * -1) as decimal(8,5));\n"
        "    END IF;\n"

        "    IF (v6_t_count > 0 AND new.transit_v6_prefixes > 0 AND new.transit_v6_prefixes != v6_t_count) THEN\n"
        "      SET new.transit_v6_change = cast(if(new.transit_v6_prefixes > v6_t_count,\n" 
        "                                   new.transit_v6_prefixes / v6_t_count,\n" 
        "                                   v6_t_count / new.transit_v6_prefixes * -1) as decimal(8,5));\n"
        "    END IF;\n"

        "    IF (v4_o_count > 0 AND new.origin_v4_prefixes > 0 AND new.origin_v4_prefixes != v4_o_count) THEN\n"
        "      SET new.origin_v4_change = cast(if(new.origin_v4_prefixes > v4_o_count,\n"
        "                                   new.origin_v4_prefixes / v4_o_count,\n"                                           
        "                                   v4_o_count / new.origin_v4_prefixes * -1) as decimal(8,5));\n"
        "    END IF;\n"

        "    IF (v6_o_count > 0 AND new.origin_v6_prefixes > 0 AND new.origin_v6_prefixes != v6_o_count) THEN\n"
        "      SET new.origin_v6_change = cast(if(new.origin_v6_prefixes > v6_o_count,\n" 
        "                                   new.origin_v6_prefixes / v6_o_count,\n" 
        "                                   v6_o_count / new.origin_v6_prefixes * -1) as decimal(8,5));\n"
        "    END IF;\n"

        "        END IF;\n"
        "    END;\n"
#        "delimiter ;\n"
)

#: 'gen_asn_stats' table schema
TBL_GEN_ASN_STATS_NAME = "gen_asn_stats"
TBL_GEN_ASN_STATS_SCHEMA = (
        "CREATE TABLE IF NOT EXISTS %s ("
        "  asn int unsigned not null,"
        "  isTransit tinyint not null default 0,"
        "  isOrigin tinyint not null default 0,"
        "  transit_v4_prefixes bigint unsigned not null default 0,"
        "  transit_v6_prefixes bigint unsigned not null default 0,"
        "  origin_v4_prefixes bigint unsigned not null default 0,"
        "  origin_v6_prefixes bigint unsigned not null default 0,"
        "  transit_v4_change DECIMAL(8,5) not null default 0,"
        "  transit_v6_change DECIMAL(8,5) not null default 0,"
        "  origin_v4_change DECIMAL(8,5) not null default 0,"
        "  origin_v6_change DECIMAL(8,5) not null default 0,"
        "  repeats bigint unsigned not null default 0,"
        "  timestamp timestamp not null default current_timestamp on update current_timestamp,"
        "  PRIMARY KEY (asn,timestamp) "
        "  ) ENGINE=InnoDB DEFAULT CHARSET=latin1 "
        ) % (TBL_GEN_ASN_STATS_NAME)

TBL_GEN_ASN_STATS_LAST_NAME = "gen_asn_stats_last"
TBL_GEN_ASN_STATS_LAST_SCHEMA = (
        "CREATE TABLE IF NOT EXISTS %s ("
        "  asn int unsigned not null,"
        "  isTransit tinyint not null default 0,"
        "  isOrigin tinyint not null default 0,"
        "  transit_v4_prefixes bigint unsigned not null default 0,"
        "  transit_v6_prefixes bigint unsigned not null default 0,"
        "  origin_v4_prefixes bigint unsigned not null default 0,"
        "  origin_v6_prefixes bigint unsigned not null default 0,"
        "  timestamp timestamp not null default current_timestamp on update current_timestamp,"
        "  PRIMARY KEY (asn) "
        "  ) ENGINE=InnoDB DEFAULT CHARSET=latin1 "
        ) % (TBL_GEN_ASN_STATS_LAST_NAME)


#: gen_active_asns table schema
TBL_GEN_ACTIVE_ASNS_NAME = "gen_active_asns"
TBL_GEN_ACTIVE_ASNS_SCHEMA = (
        "CREATE TABLE IF NOT EXISTS %s ("
        "  asn int unsigned not null,"
        "  old bit(1) NOT NULL DEFAULT b'0',"
        "  PRIMARY KEY (asn) "
        "  ) ENGINE=InnoDB DEFAULT CHARSET=latin1 "
        ) % (TBL_GEN_ACTIVE_ASNS_NAME)

# ----------------------------------------------------------------
# Queries to get data
# ----------------------------------------------------------------
#: returns a list of distinct origin/transit ASN's
QUERY_DISTINCT_ASNS = (
        "select asn from %s"
    ) % (TBL_GEN_ACTIVE_ASNS_NAME)

#: INSERT INTO distinct ASN table - This query is slow, so only run once in a while
INSERT_DISTINCT_ASNS = (
        "REPLACE INTO %s (asn,old) SELECT distinct asn,0 from as_path_analysis where isWithdrawn = False;"
    ) % (TBL_GEN_ACTIVE_ASNS_NAME)

#: INSERT current stats for each ASN
INSERT_LAST_ASN_STATS = (
        "REPLACE INTO %s (asn,isTransit,isOrigin,transit_v4_prefixes,transit_v6_prefixes,origin_v4_prefixes,origin_v6_prefixes)"
        "     SELECT asn,isTransit,isOrigin,transit_v4_prefixes,transit_v6_prefixes,origin_v4_prefixes,origin_v6_prefixes"
        "          FROM gen_asn_stats"
        "          WHERE timestamp >= date_sub(current_timestamp, interval 8 minute)"
        "          GROUP BY asn"
    ) % (TBL_GEN_ASN_STATS_LAST_NAME)

#: returns a list of distinct prefix counts for transit asn's
#:      %(asn_list)s     = Transit ASN list (comma separated)
QUERY_AS_TRANSIT_PREFIXES = (
        "SELECT asn,isIPv4,count(distinct prefix_bin,prefix_len)"
        "   FROM as_path_analysis a"
        "   WHERE asn in ( %(asn_list)s ) and asn_left != 0 and asn_right != 0"
        "       AND (isWithdrawn = False OR timestamp >= date_sub(current_timestamp, interval 30 minute))"
        "   GROUP BY asn,isIPv4 order by null"
    )

#: returns a list of distinct prefix counts for origin asn's
# QUERY_AS_ORIGIN_PREFIXES = (
#         "select origin_as,rib.isIPv4, count(distinct prefix_bin,prefix_len)"
# 	    "       FROM rib"
#         "       WHERE isWithdrawn = False"
#         "       GROUP BY origin_as,isIPv4"
#     )
QUERY_AS_ORIGIN_PREFIXES = (
        "select origin_as,rib.isIPv4, count(distinct prefix_bin,prefix_len)"
	    "       FROM rib"
        "       WHERE origin_as in ( %(asn_list)s )"
        "           AND (isWithdrawn = False OR timestamp >= date_sub(current_timestamp, interval 30 minute))"
        "       GROUP BY origin_as,isIPv4 order by null"
    )


# ----------------------------------------------------------------
# Insert statements
# ----------------------------------------------------------------


class dbAcccess:
    """ Database access class

        This class handles the database access methods.
    """

    #: Connection handle
    conn = None

    #: Cursor handle
    cursor = None

    #: Last query time in seconds (floating point)
    last_query_time = 0

    def __init__(self):
        pass

    def connectDb(self, user, pw, host, database):
        """
         Connect to database
        """
        try:
            self.conn = mysql.connect(user=user, password=pw,
                               host=host,
                               database=database)


            self.conn.set_autocommit(True)
            self.cursor = self.conn.cursor(buffered=True)


        except mysql.Error as err:
            if err.errno == mysql.errorcode.ER_ACCESS_DENIED_ERROR:
                print("Something is wrong with your user name or password")

            elif err.errno == mysql.errorcode.ER_BAD_DB_ERROR:
                print("Database does not exists")

            else:
                print("ERROR: Connect failed: " + str(err))
                raise err

    def close(self):
        """ Close the database connection """
        if (self.cursor):
            self.cursor.close()
            self.cursor = None

        if (self.conn):
            self.conn.close()
            self.conn = None

    def createTable(self, tableName, tableSchema, dropIfExists = True):
        """ Create table schema

            :param tablename:    The table name that is being created
            :param tableSchema:  Create table syntax as it would be to create it in SQL
            :param dropIfExists: True to drop the table, false to not drop it.

            :return: True if the table successfully was created, false otherwise
        """
        if (not self.cursor):
            print "ERROR: Looks like Mysql is not connected, try to reconnect."
            return False

        try:
            if (dropIfExists == True):
               self.cursor.execute("DROP TABLE IF EXISTS %s" % tableName)

            self.cursor.execute(tableSchema)

        except mysql.Error as err:
            print("ERROR: Failed to create table - " + str(err))
            #raise err
            return False

        return True

    def createTrigger(self, trigger_def, trigger_name, dropIfExists = True):
        """ Create trigger

            :param trigger_def:     Trigger definition
            :param trigger_name:    Trigger name

            :param dropIfExists:    True to drop the table, false to not drop it.

            :return: True if the table successfully was created, false otherwise
        """
        if (not self.cursor):
            print "ERROR: Looks like Mysql is not connected, try to reconnect."
            return False

        try:
            if (dropIfExists == True):
               self.cursor.execute("DROP TRIGGER IF EXISTS %s" % trigger_name)

            self.cursor.execute(trigger_def)

        except mysql.Error as err:
            print("ERROR: Failed to create trigger - " + str(err))
            return False

        return True

    def query(self, query, queryParams=None):
        """ Run a query and return the result set back

            :param query:       The query to run - should be a working SELECT statement
            :param queryParams: Dictionary of parameters to supply to the query for
                                variable substitution

            :return: Returns "None" if error, otherwise array list of rows
        """
        if (not self.cursor):
            print "ERROR: Looks like MySQL is not connected, try to reconnect"
            return None

        try:
            startTime = time()

            if (queryParams):
                self.cursor.execute(query % queryParams)
            else:
                self.cursor.execute(query)

            self.last_query_time = time() - startTime

            return self.cursor.fetchall()

        except mysql.Error as err:
            print("ERROR: query failed - " + str(err))
            return None

    def queryNoResults(self, query, queryParams=None):
        """ Runs a query that would normally not have any results, such as insert, update, delete

            :param query:       The query to run - should be a working INSERT or UPDATE statement
            :param queryParams: Dictionary of parameters to supply to the query for
                                variable substitution

            :return: Returns True if successful, false if not.
        """
        if (not self.cursor):
            print "ERROR: Looks like MySQL is not connected, try to reconnect"
            return None

        try:
            startTime = time()

            if (queryParams):
                self.cursor.execute(query % queryParams)
            else:
                self.cursor.execute(query)

            self.last_query_time = time() - startTime

            return True

        except mysql.Error as err:
            print("ERROR: query failed - " + str(err))
            # print "query: %r" % query
            return None


def UpdatePrefixesCounts(asns, query, batch_count, origin, db_pool):
    """ Update Origin prefix counts

        :param asns:         List of ASNS (distinct ASN's)
        :param query:        SQL query to run
        :param batch_count:  Number of ASN's to batch in a query
        :param origin:       Boolean; True indicates origin query, False indicates Transit query
        :param db_pool:      List of pointers to DB access class (db should already be connected and ready)
    """
    colName_transit_v4 = "transit_v4_prefixes"
    colName_transit_v6 = "transit_v6_prefixes"
    colName_origin_v4 = "origin_v4_prefixes"
    colName_origin_v6 = "origin_v6_prefixes"

    pool = ThreadPool(processes=len(db_pool))

    thrs = [ ]
    i = 0
    batch = 0
    for row in asns:
        if int(row[0]) == 23456:
            continue

        if batch < batch_count:
            if batch > 0:
                asn_list += "," + str(row[0])
            else:
                asn_list = str(row[0])

            batch += 1

        # Process batch
        if batch >= batch_count or row[0] == asns[len(asns) - 1]:
            while batch > 0:
                if i >= len(thrs) or thrs[i].ready():         # thread not yet started or previous is ready
                    if i < len(thrs) and thrs[i].ready():     # Thread ran is now ready

                        for (asn, v4, v6) in thrs[i].get():
                            if asn not in RECORD_DICT:
                                if origin:
                                    RECORD_DICT[asn] = {colName_origin_v4: v4,
                                                        colName_origin_v6: v6,
                                                        colName_transit_v4: 0,
                                                        colName_transit_v6: 0}
                                else:
                                    RECORD_DICT[asn] = {colName_origin_v4: 0,
                                                        colName_origin_v6: 0,
                                                        colName_transit_v4: v4,
                                                        colName_transit_v6: v6}

                            else:
                                if origin:
                                    RECORD_DICT[asn][colName_origin_v4] = int(v4)
                                    RECORD_DICT[asn][colName_origin_v6] = int(v6)
                                else:
                                    RECORD_DICT[asn][colName_transit_v4] = int(v4)
                                    RECORD_DICT[asn][colName_transit_v6] = int(v6)

                        thrs.pop(i)

                    thrs.insert(i, pool.apply_async(UpdatePrefixesCountsThread,
                                     (db_pool[i], query, asn_list,)))

                    batch = 0

                if i < len(db_pool) - 1:
                    i += 1
                else:
                    i = 0

                sleep(0.001)

    # Process remaining threads that are still running
    for thr in thrs:
        thr.wait()
        for (asn, v4, v6) in thr.get():
            if asn not in RECORD_DICT:
                if origin:
                    RECORD_DICT[asn] = {colName_origin_v4: v4,
                                        colName_origin_v6: v6,
                                        colName_transit_v4: 0,
                                        colName_transit_v6: 0}
                else:
                    RECORD_DICT[asn] = {colName_origin_v4: 0,
                                        colName_origin_v6: 0,
                                        colName_transit_v4: v4,
                                        colName_transit_v6: v6}

            else:
                if origin:
                    RECORD_DICT[asn][colName_origin_v4] = int(v4)
                    RECORD_DICT[asn][colName_origin_v6] = int(v6)
                else:
                    RECORD_DICT[asn][colName_transit_v4] = int(v4)
                    RECORD_DICT[asn][colName_transit_v4] = int(v4)
                    RECORD_DICT[asn][colName_transit_v6] = int(v6)


def UpdatePrefixesCountsThread(db, query, asn_list):
    """ Runs query on given DB pointer and returns tuple results

    :param db:           Pointer to DB access class (db should already be connected and ready)
    :param query:        ASN count query
    :param asn_list:   ASN list to query

    :return: tuple list of tuples (asn, v4_prefixes, v6_prefixes)
    """
    results = []
    result_dict = {}

    # Run query and store data
    rows = db.query(query % {'asn_list': asn_list})

    print "   ASNs=(%s) Query took %r seconds" % (asn_list, db.last_query_time)

    for row in rows:
        if int(row[0]) not in result_dict:
            result_dict[int(row[0])] = { "v4": 0, "v6": 0}

        if row[1] == 1:   # IPv4
            result_dict[int(row[0])]['v4'] = int(row[2])
        else:  # IPv6
            result_dict[int(row[0])]['v6'] = int(row[2])

    for asn in result_dict:
        results.append((asn, result_dict[asn]['v4'], result_dict[asn]['v6']))

    return results


def UpdateDB(db):
    """ Update DB with RECORD_DICT information

        Updates the DB for every record in RECORD_DICT

        :param db:  Pointer to DB access class (db should already be connected and ready)
    """
    query = ("INSERT IGNORE INTO %s "
              "    (asn, isTransit,isOrigin,transit_v4_prefixes,transit_v6_prefixes,"
                    "origin_v4_prefixes,origin_v6_prefixes,timestamp,repeats) "
                  " VALUES ") % (TBL_GEN_ASN_STATS_NAME)

    VALUE_fmt=" ('%s',%d,%d,'%s','%s','%s','%s',from_unixtime(%s),0)"

    isTransit = 0
    isOrigin = 0
    totalRecords = len(RECORD_DICT)

    if (totalRecords == 0):
        print "No records, skipping update"
        return

    for idx,asn in enumerate(RECORD_DICT):
        if (RECORD_DICT[asn]['transit_v4_prefixes'] > 0 or RECORD_DICT[asn]['transit_v6_prefixes'] > 0):
            isTransit = 1
        else:
            isTransit = 0

        if (RECORD_DICT[asn]['origin_v4_prefixes'] > 0 or RECORD_DICT[asn]['origin_v6_prefixes'] > 0):
            isOrigin = 1
        else:
            isOrigin = 0

        query += VALUE_fmt % (asn,isTransit,isOrigin, RECORD_DICT[asn]['transit_v4_prefixes'],
                             RECORD_DICT[asn]['transit_v6_prefixes'],RECORD_DICT[asn]['origin_v4_prefixes'],
                             RECORD_DICT[asn]['origin_v6_prefixes'], str(INTERVAL_TIMESTAMP))

        if (idx <  totalRecords-1):
            query += ','

        # squery = VALUE_fmt % (asn,isTransit,isOrigin, RECORD_DICT[asn]['transit_v4_prefixes'],
        #                      RECORD_DICT[asn]['transit_v6_prefixes'],RECORD_DICT[asn]['origin_v4_prefixes'],
        #                      RECORD_DICT[asn]['origin_v6_prefixes'], str(INTERVAL_TIMESTAMP))
        # squery += " ON DUPLICATE KEY UPDATE repeats=repeats+1"
        #
        # db.queryNoResults(query + squery)

    query += " ON DUPLICATE KEY UPDATE repeats=repeats+1"

    print "Running bulk insert/update"
    db.queryNoResults(query)

    print "Bulk insert took %r seconds" % (db.last_query_time)

    print "---- Updating last ASN stats: %s " % datetime.now()
    db.queryNoResults(INSERT_LAST_ASN_STATS)


def main():
    """
    """
    cmd = ['bash', '-c', "source /etc/default/openbmpd && set"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE)

    for line in proc.stdout:
       (key, _, value) = line.partition("=")
       os.environ[key] = value.rstrip()

    proc.communicate()

    db = dbAcccess()
    db.connectDb(os.environ["OPENBMP_DB_USER"], os.environ["OPENBMP_DB_PASSWORD"], "localhost", os.environ["OPENBMP_DB_NAME"])

    if len(sys.argv) > 1 and sys.argv[1] == "init":
        print "---- INIT: Creating tables and loading data: %s " % datetime.now()
        # Create the table
        db.createTable(TBL_GEN_ASN_STATS_NAME, TBL_GEN_ASN_STATS_SCHEMA, False)

        db.createTable(TBL_GEN_ASN_STATS_LAST_NAME, TBL_GEN_ASN_STATS_LAST_SCHEMA, False)

        # Create trigger
        db.createTrigger(TRIGGER_CREATE_INSERT_STATUS_DEF, TRIGGER_INSERT_STATUS_NAME, True)

        # Create distinct table
        db.createTable(TBL_GEN_ACTIVE_ASNS_NAME, TBL_GEN_ACTIVE_ASNS_SCHEMA, False)

        # Update active asn table to mark all objects as old
        db.queryNoResults("UPDATE %s SET old = 1" % TBL_GEN_ACTIVE_ASNS_NAME)

        # Update all ASN's
        db.queryNoResults(INSERT_DISTINCT_ASNS)

        # Remove old records
        db.queryNoResults("DELETE from %s WHERE old = 1" % TBL_GEN_ACTIVE_ASNS_NAME)


    else:
        distinct_asns = db.query(QUERY_DISTINCT_ASNS)

        print "Distinct ASN list Query took %r seconds, total rows %d" % (db.last_query_time, len(distinct_asns))

        # Open additional connections to DB for parallel queries
        db_pool = []
        for i in range(0, 20):
            dbp = dbAcccess()
            dbp.connectDb(os.environ["OPENBMP_DB_USER"], os.environ["OPENBMP_DB_PASSWORD"], "localhost", os.environ["OPENBMP_DB_NAME"])
            db_pool.append(dbp)

        print "---- Getting Origins: %s " % datetime.now()
        UpdatePrefixesCounts(distinct_asns, QUERY_AS_ORIGIN_PREFIXES, MAX_BATCH_SELECT_ORIGIN, True, db_pool)

        print "---- Getting Transits: %s " % datetime.now()
        UpdatePrefixesCounts(distinct_asns, QUERY_AS_TRANSIT_PREFIXES, MAX_BATCH_SELECT_TRANSIT, False, db_pool)

        for d in db_pool:
            d.close()

        print "RECORD_DICT length = %d" % len(RECORD_DICT)

        # RECORD_DICT now has all information, ready to update DB
        print "---- Updating DB: %s " % datetime.now()
        UpdateDB(db)

    print "---- DONE: %s " % datetime.now()
    db.close()


def script_exit(status=0):
    """ Simple wrapper to exit the script cleanly """
    exit(status)

if __name__ == '__main__':
    main()
