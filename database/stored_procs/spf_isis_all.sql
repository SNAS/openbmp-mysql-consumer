# -----------------------------------------------------------------------------
# Copyright (c) 2015-2017 Cisco Systems, Inc. and others.  All rights reserved.
#
# IS-IS SPF MySQL stored procedure to run SPF for all nodes for a given peer.
#
# Syntax: call spf_isis_all(<peer_hash_id>,
#                           @min_iterations, @max_iterations, @node_count);
#
# Details:
#   Will create/update new memory table named 'igp_isis_<peer_hash_id>'
#   with the IGP routing table information.  The 'best' boolean indicates if
#   the prefix is the lowest cost/selected prefix. This will be performed on
#   all nodes for the given peer.
# -----------------------------------------------------------------------------
drop procedure if exists spf_isis_all;
DELIMITER //
CREATE PROCEDURE spf_isis_all (IN peer_hash_id char(32),
                               OUT min_iterations int,
                               OUT max_iterations int,
                               OUT node_count int)
BEGIN
    declare igp_rib_table_name varchar(64) default 'igp_isis';
    declare done tinyint default FALSE;
    declare no_more_rows tinyint default FALSE;
    declare node_hash_id char(32);
    declare mt_id int unsigned default 0;

    declare node_cursor CURSOR FOR
        SELECT hash_id FROM ls_nodes n where n.peer_hash_id = peer_hash_id
            AND n.isWithdrawn = False;

    declare CONTINUE HANDLER
        FOR NOT FOUND SET no_more_rows = TRUE;

    set node_count = 0;
    set min_iterations = 0;
    set max_iterations = 0;

    set igp_rib_table_name = concat(igp_rib_table_name,'_', peer_hash_id);

    # drop rib table if it exists
    set @sql_text = concat(
        "drop table IF EXISTS ", igp_rib_table_name, ";");

    PREPARE stmt FROM @sql_text;
    EXECUTE stmt;

    OPEN node_cursor;
    WHILE NOT done DO
        FETCH node_cursor INTO node_hash_id;

        IF no_more_rows THEN
            set done = TRUE;

        ELSE
            # Loop through each mt-id (doesn't matter if it exists or not)
            set mt_id = 0;
            mt_loop: LOOP

                CALL spf_isis(peer_hash_id, node_hash_id, mt_id, @spf_iters);
                IF min_iterations = 0 OR @spf_iters < min_iterations THEN
                    set min_iterations = @spf_iters;
                END IF;

                IF max_iterations < @spf_iters THEN
                    set max_iterations = @spf_iters;
                END IF;

                # Default is IPv4/standard
                IF mt_id = 0 THEN
                    set mt_id = 2;      # IPv6
                ELSE
                  IF mt_id = 2 THEN
                      set mt_id = 3;    # multicast
                  ELSE
                      LEAVE mt_loop;    # No more mt-ids of interest
                  END IF;

                END IF;
            END LOOP mt_loop;

            set node_count = node_count + 1;

        END IF;
    END WHILE;
    CLOSE node_cursor;

    SELECT concat("Nodes: ", node_count, " iterations min: ", min_iterations,
                  " max: ", max_iterations) as Summary;
END
//
delimiter ;
