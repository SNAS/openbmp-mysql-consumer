#!/usr/bin/env python
"""
  Copyright (c) 2013-2016 Cisco Systems, Inc. and others.  All rights reserved.

  This program and the accompanying materials are made available under the
  terms of the Eclipse Public License v1.0 which accompanies this distribution,
  and is available at http://www.eclipse.org/legal/epl-v10.html

  .. moduleauthor:: Tim Evens <tievens@cisco.com> Junze Bao <junbao@cisco.com>
"""
import sys
import getopt
import gzip
import dbAccess
import requests
import os
from datetime import datetime
from collections import OrderedDict
from time import sleep
from ftplib import FTP

# ----------------------------------------------------------------
# Whois mapping
# ----------------------------------------------------------------
ORG_MAP = {
    # arin
    'handle': 'org_id',
    'name': 'org_name',
    'streetAddress': 'address',
    'city': 'city',
    'iso3166-2': 'state_prov',
    'postalCode': 'postal_code',
    'iso3166-1': 'country',
    # ripe
    'as-name': 'as_name',  # apnic
    'descr': 'remarks',
    'org': 'org_id',
    'organisation': 'org_id',
    'org-name': 'org_name',
    # apnic
    'country': 'country',
    'remars': 'remarks',
}

APNIC_ATTR_MAP = {
    'aut-num': 'asn',
    'as-name': 'as_name',
    'country': 'country',
    'remars': 'remarks'
}

APNIC_FILENAME = 'apnic.db.aut-num.gz'
TOTAL_COLUMN_NUMBER = 13

# ----------------------------------------------------------------
# Tables schema
# ----------------------------------------------------------------

#: 'gen_whois_asn' table schema
TBL_GEN_WHOIS_ASN_NAME = "gen_whois_asn"
TBL_GEN_WHOIS_ASN_SCHEMA = (
                               "CREATE TABLE IF NOT EXISTS %s ("
                               "  asn int unsigned not null,"
                               "  as_name varchar(128),"
                               "  org_id varchar(64),"
                               "  org_name varchar(255),"
                               "  remarks text,"
                               "  address varchar(255),"
                               "  city varchar(64),"
                               "  state_prov varchar(32),"
                               "  postal_code varchar(32),"
                               "  country varchar(24),"
                               "  raw_output text,"
                               "  source varchar(64),"
                               "  timestamp timestamp not null default current_timestamp on update current_timestamp,"
                               "  PRIMARY KEY (asn) "
                               "  ) ENGINE=InnoDB DEFAULT CHARSET=latin1 "
                           ) % (TBL_GEN_WHOIS_ASN_NAME)

# ----------------------------------------------------------------
# Whois source url
# ----------------------------------------------------------------

WHOIS_SOURCES = OrderedDict()
WHOIS_SOURCES['arin'] = "http://whois.arin.net/rest/asn/"
#WHOIS_SOURCES['ripe'] = "http://rest.db.ripe.net/ripe/aut-num/as"

# ----------------------------------------------------------------
# Queries to get data
# ----------------------------------------------------------------

#: Gets a list of all distinct ASN's
QUERY_AS_LIST = (
    "select distinct a.asn"
    "   from gen_asn_stats a left join gen_whois_asn w on (a.asn = w.asn)"
    "   where isnull(as_name)"
)


def get_asn_list(db):
    """ Gets the ASN list from DB

        :param db:    instance of DbAccess class

        :return: Returns a list/array of ASN's
    """
    # Run query and store data
    rows = db.query(QUERY_AS_LIST)
    print "Query for ASN List took %r seconds" % (db.last_query_time)

    print "total rows = %d" % len(rows)

    asn_list = []

    # Append only if the ASN is not a private/reserved ASN
    for row in rows:
        try:
            asn_int = long(row[0])

            if (asn_int == 0 or asn_int == 23456 or
                    (asn_int >= 64496 and asn_int <= 65535) or
                    (asn_int >= 65536 and asn_int <= 131071) or
                        asn_int >= 4200000000):
                pass
            else:
                asn_list.append(row[0])

        except:
            pass

    return asn_list


def whois(asn, source):
    """ whois the source for the given ASN

    :param asn:
    :param source:
    :return: dict of parsed attribute/values
    """
    record = {}

    if source == 'arin':
        record = arin(asn)
    elif source == 'ripe':
        record = ripe(asn)

    return record


def arin(asn):
    """
    use arin rest api to search asn
    :param asn: target asn
    :return: a dict of record
    """
    record = {}
    headers = {'Accept': 'application/json'}

    response = requests.get(WHOIS_SOURCES['arin'] + str(asn), headers=headers)

    if response.status_code == 200:
        res_json = response.json()
        asn_json = res_json['asn']
        # if return value points to ripe, search ripe
        if 'ripe' in asn_json['name']['$'].lower():
            #record = ripe(asn)
            return 'RIPE'
        elif 'apnic' in asn_json['name']['$'].lower():
            return 'AS in APNIC'
        else:
            # add attributes in asn
            record['as_name'] = asn_json['name']['$']
            if 'comment' in asn_json:
                if isinstance(asn_json['comment']['line'], list):
                    for l in asn_json['comment']:
                        remarks = ' '.join(l['$'] for l in asn_json['comment']['line'])
                elif '$' in asn_json['comment']['line']:
                    remarks = asn_json['comment']['line']['$']
                else:
                    remarks = ''

                record['remarks'] = remarks.replace("'", "\"")

            # fetch attributes related to org
            org_ref = res_json['asn']['orgRef']['$']
            org_json = requests.get(org_ref, headers=headers).json()
            org = org_json['org']

            for key, value in org.iteritems():
                try:
                    if key in ORG_MAP:
                        # value could be {'$': xx}
                        # and {'line': [{..}, {}]}, {'line': {..}}
                        if '$' in value:
                            record[ORG_MAP[key]] = value['$']
                        elif key == 'iso3166-1':
                            record['country'] = value['code2']['$']
                        elif 'line' in value:
                            if isinstance(value['line'], list):
                                record[ORG_MAP[key]] = ' '.join(l['$'] for l in value['line'])
                            else:
                                record[ORG_MAP[key]] = value['line']['$']
                except:
                    pass

    else:
        print 'ERROR: ' + str(response.status_code)

    return record


def ripe(asn):
    """
    use ripe rest api to search asn
    :param asn:
    :return:
    """
    record = {}
    headers = {'Accept': 'application/json'}

    response = requests.get(WHOIS_SOURCES['ripe'] + str(asn), headers=headers)
    if response.status_code != 200:
        print 'ERROR: RIPE request returned status code ' + str(response.status_code)
    else:
        res_json = response.json()
        as_attribute = res_json['objects']['object'][0]['attributes']['attribute']  # a list
        for attr in as_attribute:
            if 'name' in attr and attr['name'] in ORG_MAP:
                record[ORG_MAP[attr['name']]] = attr['value']

        if 'org' not in record:  # find org URL
            if 'org_id' in record:
                record['org'] = record['org_id']
            else:
                print 'ERROR: Organization info not found asn: %d %r' % (asn, record)
                record['org'] = ""

        org_id = record['org']
        del record['org']
        org_res_json = requests.get('http://rest.db.ripe.net/ripe/organisation/' + org_id, headers=headers).json()

        if 'objects' in org_res_json:
            org_attribute = org_res_json['objects']['object'][0]['attributes']['attribute']
            addr_list = []
            for attr in org_attribute:
                if 'name' in attr and attr['name'] in ORG_MAP:
                    record[ORG_MAP[attr['name']]] = attr['value']
                elif attr['name'] == 'address':
                    # TODO: extract postal code with regex
                    addr_list.append(attr['value'])
            # process address
            try:
                record['address'] = addr_list[0] + ', ' + addr_list[1]
                record['city'] = addr_list[2]
                record['country'] = addr_list[3]
            except IndexError, e:
                print 'Error: Address returned by RIPE are not in good format: ' + e.message

    return record


def apnic(db):
    """
    download bulk data from apnic server and load it to Database
    :param db: database object
    """
    download_file()
    load_file_to_db(db)
    os.remove(APNIC_FILENAME)


def download_file():
    """
    download apnic data from ftp server
    """
    print 'Start downloading...'
    ftp = FTP('ftp.apnic.net')
    ftp.login('ardently', 'itchiest56')
    ftp.cwd('pub/whois-data/APNIC/split')
    ftp.retrbinary('retr ' + APNIC_FILENAME, open(APNIC_FILENAME, 'wb').write)
    ftp.quit()
    print 'Download complete!'


def load_file_to_db(db):
    """
    Load file apnic.db.aut-num.gz to db
    """
    record = {}

    f = gzip.open(APNIC_FILENAME, 'rb')
    if f is not None:
        temp_addr_list = []
        for line in f:
            # skip blank lines and comments
            if len(line) == 0 or line[0] == '%' or line[0] == '#':
                continue
            line = line.rstrip('\n')
            if len(line) == 0:
                # end of one record, process address
                if len(temp_addr_list) == 4:
                    record['address'] = temp_addr_list[0] + ', ' + temp_addr_list[1]
                    record['city'] = temp_addr_list[2]
                    record['postal_code'] = temp_addr_list[-1]
                elif len(temp_addr_list) == 3:
                    record['address'] = temp_addr_list[0] + ', ' + temp_addr_list[1]
                    record['city'] = temp_addr_list[2]
                elif len(temp_addr_list) == 2:
                    record['address'] = temp_addr_list[0] + ', ' + temp_addr_list[1]
                elif len(temp_addr_list) == 1:
                    record['address'] = temp_addr_list[0]
                temp_addr_list = []
                # add to db
                if 'asn' in record:
                    add_record_to_db(db, record)
                record = {}

            elif ':' in line:
                (attr, value) = line.split(':', 1)
                attr = attr.strip()
                value = value.strip()
                if attr == 'descr':
                    temp_addr_list.append(value)
                if attr in APNIC_ATTR_MAP:
                    if '#' in value:
                        value = value.split('#')[0].strip()
                    if attr == 'aut-num':
                        value = value[2:]  # remove AS
                    if APNIC_ATTR_MAP[attr] in record:
                        record[APNIC_ATTR_MAP[attr]] = record[APNIC_ATTR_MAP[attr]] + ' ' + value
                    else:
                        record[APNIC_ATTR_MAP[attr]] = value

    f.close()


def add_record_to_db(db, record, commit=False):
    columns = ''
    values = ''
    for index, name in enumerate(record, start=1):
        columns += name
        if name == 'asn':
            values += record[name]
        else:
            values += '"' + record[name].replace('"', "'") + '"'
        if index < TOTAL_COLUMN_NUMBER:
            columns += ', '
            values += ', '
    query = "REPLACE INTO gen_whois_asn (" + columns + "source" + ") VALUES (" + values + "'apnic'" + ")"
    db.queryNoResults(query)


def walk_whois(db, asn_list):
    """ Walks through the ASN list

        The walk will pace each query and will add a delay ever 100 queries
        in order to not cause abuse.  The whois starts with arin and
        follows the referral.

        :param db:         DbAccess reference
        :param asn_list:    ASN List to

        :return: Returns a list/array of ASN's (rows from return set)
    """
    asn_list_size = len(asn_list)
    asn_list_processed = 0

    # Max number of requests before requiring a delay
    MAX_REQUESTS_PER_INTERVAL = 100

    requests_num = 0

    for asn in asn_list:
        requests_num += 1
        asn_list_processed += 1

        # Try all sources
        for source in WHOIS_SOURCES:
            record = whois(asn, source)
            if 'as_name' in record:
                record['source'] = source
                break
            elif 'APNIC' in record:  # do not look forward if AS is in APNIC db
                break

        # Only process the record if whois responded
        if 'as_name' in record:

            # Update record to add country and state if it has an address
            if 'address' in record:
                addr = record['address'].split('\n')
                if 'country' not in record:
                    record['country'] = addr[len(addr) - 1]

                if 'state_prov' not in record:
                    record['state_prov'] = addr[len(addr) - 2]
                    record['state_prov'] = record['state_prov'][:32] 

            # Check if as_name is missing, if so use org_name
            if 'as_name' not in record and 'org_id' in record:
                record['as_name'] = record['org_id']

            # Truncate records
            if 'country' in record:
                record['country'] = record['country'][:24]

            # Update database with required
            update_whois_db(db, asn, record)

        # delay between queries
        if requests_num >= MAX_REQUESTS_PER_INTERVAL:
            print "%s: Processed %d of %d" % (datetime.utcnow(), asn_list_processed, asn_list_size)
            sleep(15)
            requests_num = 0


def update_whois_db(db, asn, record):
    """ Update the whois info in the DB

        :param db:          DbAccess reference
        :param asn:         ASN to update in the DB
        :param record:      Dictionary of column names and values
                            Key names must match the column names in DB/table

        :return: True if updated, False if error
    """
    total_columns = len(record)

    # get query column list and value list
    columns = ''
    values = ''
    for idx, name in enumerate(record, start=1):
        columns += name
        values += '\'' + record[name] + '\''

        if (idx != total_columns):
            columns += ','
            values += ','

    # Build the query
    query = ("REPLACE INTO %s "
             "    (asn,%s) VALUES ('%s',%s) ") % (TBL_GEN_WHOIS_ASN_NAME, columns, asn, values)

    print "QUERY = %s" % query
    db.queryNoResults(query)


def script_exit(status=0):
    """ Simple wrapper to exit the script cleanly """
    exit(status)


def parse_cmd_args(argv):
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
    REQUIRED_ARGS = 3
    found_req_args = 0
    cmd_args = {'user': None,
                'password': None,
                'db_host': None}

    if len(argv) < 3:
        usage(argv[0])
        sys.exit(1)

    try:
        (opts, args) = getopt.getopt(argv[1:], "hu:p:",
                                     ["help", "user", "password"])

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

            else:
                usage(argv[0])
                sys.exit(1)

        # The last arg should be the command
        if len(args) <= 0:
            print "ERROR: Missing the database host/IP"
            usage(argv[0])
            sys.exit(1)

        else:
            found_req_args += 1
            cmd_args['db_host'] = args[0]

        # The last arg should be the command
        if found_req_args < REQUIRED_ARGS:
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
    print ""

    print "OPTIONAL OPTIONS:"
    print "  -h, --help".ljust(30) + "Print this help menu"


def main():
    cfg = parse_cmd_args(sys.argv)

    db = dbAccess.dbAcccess()
    db.connectDb(cfg['user'], cfg['password'], cfg['db_host'], "openBMP")

    # Create the table
    db.createTable(TBL_GEN_WHOIS_ASN_NAME, TBL_GEN_WHOIS_ASN_SCHEMA, False)

    asn_list = get_asn_list(db)
    walk_whois(db, asn_list)
    apnic(db)

    db.close()


if __name__ == '__main__':
    main()
