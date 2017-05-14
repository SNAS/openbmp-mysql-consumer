#!/usr/bin/env python
"""
  Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.

  This program and the accompanying materials are made available under the
  terms of the Eclipse Public License v1.0 which accompanies this distribution,
  and is available at http://www.eclipse.org/legal/epl-v10.html

  .. moduleauthor:: Junze Bao <junbao@cisco.com>
"""

import sys
import getopt
import mysql.connector as mysql
import urllib2, json
from time import time

TBL_RPKI_VALIDATOR_NAME = 'rpki_validator'
TBL_RPKI_VALIDATOR_SCHEMA = """
CREATE TABLE IF NOT EXISTS %s (
    prefix VARBINARY(16) NOT NULL,
    prefix_len tinyint UNSIGNED DEFAULT '0' NOT NULL,
    prefix_len_max tinyint UNSIGNED DEFAULT '0' NOT NULL,
    origin_as INT unsigned NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW() NOT NULL,
    KEY idx_origin (origin_as),
    KEY idx_prefix (prefix),
    KEY idx_prefix_full (prefix,prefix_len),
    CONSTRAINT `PRIMARY` PRIMARY KEY (prefix, prefix_len, prefix_len_max, origin_as)
    ) """ % TBL_RPKI_VALIDATOR_NAME

TBL_RPKI_GEN_PREFIX_NAME = 'gen_prefix_validation'
TBL_RPKI_GEN_PREFIX_SCHEMA = """
CREATE TABLE IF NOT EXISTS %s (
    prefix VARBINARY(16) NOT NULL,
    prefix_len tinyint UNSIGNED DEFAULT '0' NOT NULL,
    recv_origin_as INT unsigned NOT NULL,
    rpki_origin_as INT unsigned,
    irr_origin_as INT unsigned,
    irr_source varchar(32),
    timestamp TIMESTAMP DEFAULT NOW() NOT NULL,
    KEY idx_origin (recv_origin_as),
    KEY idx_prefix (prefix),
    KEY idx_prefix_full (prefix,prefix_len),
    PRIMARY KEY (prefix, prefix_len, recv_origin_as)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 """ % TBL_RPKI_GEN_PREFIX_NAME

TBL_RPKI_HISTORY_NAME = 'rpki_history_stats'
TBL_RPKI_HISTORY_SCHEMA = """
CREATE TABLE IF NOT EXISTS %s (
    total_prefix int(10) unsigned NOT NULL,
    total_violations int(10) unsigned NOT NULL,
    timestamp timestamp(6) NOT NULL DEFAULT current_timestamp(6),
    PRIMARY KEY (timestamp),
    KEY idx_timestamp (timestamp) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1
""" % TBL_RPKI_HISTORY_NAME

QUERY_UPDATE_GEN_PREFIX_TABLE = """
INSERT INTO %s (prefix,prefix_len,recv_origin_as,rpki_origin_as,irr_origin_as,irr_source)
  SELECT SQL_BIG_RESULT rib.prefix_bin,rib.prefix_len,rib.origin_as as recv_origin_as,
                rpki.origin_as as rpki_origin_as, w.origin_as as irr_orign_as,w.source
       FROM rib
            LEFT JOIN gen_whois_route w ON (rib.prefix_bin = w.prefix AND
                    rib.prefix_len = w.prefix_len)
            LEFT JOIN rpki_validator rpki ON (rib.prefix_bin = rpki.prefix AND
                    rib.prefix_len >= rpki.prefix_len and rib.prefix_len <= rpki.prefix_len_max)
        where rib.isWithdrawn = False and rib.origin_as > 0 and rib.origin_as != 23456
        group by rib.prefix_bin,rib.prefix_len,rib.origin_as
   ON DUPLICATE KEY UPDATE rpki_origin_as=values(rpki_origin_as),
                           irr_origin_as=values(irr_origin_as),
                           irr_source=values(irr_source);
""" % TBL_RPKI_GEN_PREFIX_NAME

MAX_BULK_INSERT_QUEUE_SIZE = 2000

def load_export(db, server, api="export.json"):
    # get json data
    data = []

    try:
        json_response = json.load(urllib2.urlopen('http://' + server + '/' + api))
        data = json_response['roas'] # json

    except urllib2.URLError as err:
       print "Error connecting to rpki server: %r" % err
       return

    query = 'REPLACE INTO rpki_validator (prefix, prefix_len, prefix_len_max, origin_as, timestamp) VALUES '
    counter = 0
    for line in data:
        asn, prefix, max_length = line['asn'][2:], line['prefix'], line['maxLength']
        prefix, prefix_len = prefix.split('/')[0], prefix.split('/')[1]
        query += '(inet6_aton("%s"), %d, %d, %d, NOW())' % (prefix, int(prefix_len), int(max_length), int(asn))
        counter += 1
        if counter < MAX_BULK_INSERT_QUEUE_SIZE:
            query += ', '
        else:
            db.queryNoResults(query)
            query = 'REPLACE INTO rpki_validator (prefix, prefix_len, prefix_len_max, origin_as, timestamp) VALUES '
            counter = 0

    # process remaining items in the query
    if query.endswith(', '):
        query = query[:-2]
        db.queryNoResults(query)

def parseCmdArgs(argv):
    """ Parse commandline arguments

        Usage is printed and program is terminated if there is an error.

        :param argv:   ARGV as provided by sys.argv.  Arg 0 is the program name

        :returns:  dictionary defined as::
                {
                    user:       <username>,
                    password:   <password>,
                    db_host:    <database host>
                }
    """
    REQUIRED_ARGS = 4
    found_req_args = 0
    cmd_args = { 'user': None,
                 'password': None,
                 'db_host': None,
                 'db_name': "openBMP",
                 'server': None,
                 'api': None }

    if (len(argv) < 5):
        usage(argv[0])
        sys.exit(1)

    try:
        (opts, args) = getopt.getopt(argv[1:], "hu:p:d:s:a:",
                                       ["help", "user=", "password=", "dbName=", "server=", 'api='])

        for o, a in opts:
            if o in ("-h", "--help"):
                usage(argv[0])
                sys.exit(0)

            elif o in ("-u", "--user"):
                found_req_args += 1
                cmd_args['user'] = a

            elif o in ("-p", "--password"):
                found_req_args += 1
                cmd_args['password'] = a

            elif o in ("-d", "--dbName"):
                found_req_args += 1
                cmd_args['db_name'] = a

            elif o in ("-s", "--server"):
                found_req_args += 1
                cmd_args['server'] = a

            else:
                usage(argv[0])
                sys.exit(1)

        # The last arg should be the command
        if (len(args) <= 0):
            print "ERROR: Missing the database host/IP"
            usage(argv[0])
            sys.exit(1)

        else:
            found_req_args += 1
            cmd_args['db_host'] = args[0]


        # The last arg should be the command
        if (found_req_args < REQUIRED_ARGS):
            print "ERROR: Missing required args, found %d required %d" % (found_req_args, REQUIRED_ARGS)
            usage(argv[0])
            sys.exit(1)

        return cmd_args

    except (getopt.GetoptError, TypeError), err:
        print str(err)  # will print something like "option -a not recognized"
        usage(argv[0])
        sys.exit(2)

def usage(prog):
    """ Usage - Prints the usage for this program.

        :param prog:  Program name
    """
    print ""
    print "Usage: %s [OPTIONS] <database host/ip address>" % prog
    print ""
    print "  -u, --user".ljust(30) + "Database username"
    print "  -p, --password".ljust(30) + "Database password"
    print "  -s, --server".ljust(30) + "RPKI Validator server address"
    print ""

    print "OPTIONAL OPTIONS:"
    print "  -h, --help".ljust(30) + "Print this help menu"
    print "  -d, --dbName".ljust(30) + "Database name, default is 'openBMP'"

    print "NOTES:"
    print "   RPKI validator http://server/export.json is used to populate the DB"

def main():
    cfg = parseCmdArgs(sys.argv)

    db = dbAcccess()
    db.connectDb(cfg['user'], cfg['password'], cfg['db_host'], cfg['db_name'])
    print 'connected to db'

    # create tables
    #db.createTable(TBL_RPKI_GEN_PREFIX_NAME, TBL_RPKI_GEN_PREFIX_SCHEMA, False)
    #db.createTable(TBL_RPKI_VALIDATOR_NAME, TBL_RPKI_VALIDATOR_SCHEMA, True)
    #print 'created tables successfully'

    # disable strict mode for session
    db.queryNoResults("SET SESSION sql_mode = ''")

    # Record old stats before pulling new data
    total_count = db.query("SELECT count(*) count FROM %s" % TBL_RPKI_VALIDATOR_NAME)[0][0]
    total_violations = db.query("SELECT count(*) FROM %s WHERE rpki_origin_as IS NOT NULL AND recv_origin_as != rpki_origin_as"
                                % TBL_RPKI_GEN_PREFIX_NAME)[0][0]
    db.queryNoResults("INSERT IGNORE INTO %s (total_prefix, total_violations) VALUES (%d, %d)"
                      % (TBL_RPKI_HISTORY_NAME, total_count, total_violations))
    print "Recorded current stats"

    server = cfg['server']
    load_export(db, server);
    print "Loaded rpki roas"

    # Purge old entries that didn't get updated
    db.queryNoResults("DELETE FROM %s WHERE timestamp < date_sub(current_timestamp, interval 1 hour)" % TBL_RPKI_VALIDATOR_NAME)
    print "purged old roas"


    db.queryNoResults(QUERY_UPDATE_GEN_PREFIX_TABLE)

    print "Updated prefix validation table"

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
            self.conn = mysql.connect(user=user, passwd=pw,
                               host=host,
                               db=database)
            self.conn.autocommit = True
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


        return True

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
            self.conn.commit()

        except mysql.Error as err:
            print("ERROR: Failed to create table - " + str(err))
            #raise err
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

            rows = []

            while (True):
                result = self.cursor.fetchmany(size=10000)
                if (len(result) > 0):
                    rows += result
                else:
                    break;

            return rows

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

            #self.conn.commit()

            self.last_query_time = time() - startTime

            return True

        except mysql.Error as err:
            print("ERROR: query failed - " + str(err))
            # print("   QUERY: %s", query)
            return None


if __name__ == '__main__':
    main()
