package org.openbmp.helpers;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */

import com.sun.org.apache.xpath.internal.operations.Bool;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IpAddr class provides helper methods for handling IP addresses
 */
public class IpAddr {

    /**
     * Check if IP is IPv4 or IPv6
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     *
     * @return True if IPv4, False if IPv6 and null if error/invalid
     */
    public static Boolean isIPv4(String ip_address) {
        Boolean ipv4 = null;

        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(ip_address);
            byte [] addr = ipaddr.getAddress();
            if (addr.length > 4)
                ipv4 = false;
            else
                ipv4 = true;

        } catch (UnknownHostException e) {
            // Ignore
        }

        return ipv4;
    }


    /**
     * Convert printed IP to byte array
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     *
     * @return IP address in byte array form
     */
    public static byte [] getIpBytes(String ip_address) {
        byte [] addr = null;

        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(ip_address);
            addr = ipaddr.getAddress();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return addr;
    }

    /**
     * Resolve IP address to FQDN
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     *
     * @return Null if error or not found, otherwise the hostname
     */
    public static String resolveIp(String ip_address) {
        String hostname = new String();

        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(ip_address);
            hostname = ipaddr.getCanonicalHostName();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return hostname;
    }

    /**
     * Convert IP to mysql varbinary HEX form for insert/update
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     *
     * @return HEX string value that can be used to insert/update a varbinary column in MySQL
     */
    public static String getIpHex(String ip_address) {
        StringBuilder hex_binary_ip = new StringBuilder();

        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(ip_address);
            byte [] addr = ipaddr.getAddress();

            for (int i = 0; i < addr.length; i++) {
                hex_binary_ip.append(String.format("%02X", addr[i]));
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return hex_binary_ip.toString();
    }

    /**
     * Convert IP to mysql  binary string form for insert/update
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     *
     * @return bit string value that can be used to insert/update a varbinary column in MySQL
     */
    public static String getIpBits(String ip_address) {
        StringBuilder bit_ip = new StringBuilder();

        InetAddress ipaddr = null;
        try {
            ipaddr = InetAddress.getByName(ip_address);
            byte [] addr = ipaddr.getAddress();

            for (int i = 0; i < addr.length; i++) {
                for (int i_bit = 7; i_bit >= 0; i_bit--) {
                    if ((addr[i] & (1 << i_bit)) != 0) {
                        bit_ip.append('1');
                    } else {
                        bit_ip.append('0');
                    }
                }
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return bit_ip.toString();
    }

    /**
     * Get IP broadcast in HEX form for mysql varbinary insert/update
     *
     * @param ip_address        IP address in printed form (IPv4 or IPv6)
     * @param bits              IP network length in bits
     *
     * @return HEX string value that can be used to insert/update a varbinary column in MySQL
     */
    public static String getIpBroadcastHex(String ip_address, int bits) {
        StringBuilder hex_binary_ip = new StringBuilder();

        InetAddress ipaddr = null;

        // If length is zero, then it's the default - set broacast to all ones
        if (bits == 0) {
            int bytes = 4;

            if (ip_address.indexOf(':') >= 0) {
                bytes = 16;
            }

            for (int i = 0; i < bytes; i++) {
                hex_binary_ip.append("FF");
            }

        }
        else {

            try {
                ipaddr = InetAddress.getByName(ip_address);
                byte[] addr = ipaddr.getAddress();

                int remaining_bits = (addr.length * 8) - bits;

                BigInteger bcast = new BigInteger(addr);

                int i;

                // Set host bits
                for (i = 0; i < remaining_bits; i++) {
                    if (!bcast.testBit(i))
                        bcast = bcast.flipBit(i);
                }

                addr = bcast.toByteArray();

                // format the broadcast IP in hex
                for (i = 0; i < addr.length; i++) {
                    hex_binary_ip.append(String.format("%02X", addr[i]));
                }

            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        return hex_binary_ip.toString();
    }
}
