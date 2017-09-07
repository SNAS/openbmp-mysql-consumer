# --------------------------------------------------------------------------------------
# Copyright (c) 2015-2017 Cisco Systems, Inc. and others.  All rights reserved.
#
# OSPF SPF MySQL stored procedure:
#
# Syntax: call ls_ospf_spf(<root router id>, @spf_iterations);
#
# Details:
#   Will generate or create a new memory table named 'igp_rib_<root router id>'
#   with the IGP routing table information.  The 'best' boolean indicates if the
#   prefix is the lowest cost/selected prefix.
# --------------------------------------------------------------------------------------

drop procedure if exists ls_ospf_spf;
DELIMITER //
CREATE PROCEDURE ls_ospf_spf (IN peer_hash_id char(32),
                              IN root_router_id varchar(46), IN max_table_age int,
                              OUT spf_iterations int)
BEGIN
    declare igp_rib_table_name varchar(64) default 'igp_ospf';
    declare node_hash_id char(32);
    declare root_node_hash_id char(32);
    declare node_router_id char(46);
    declare nh_node_hash_id char(32);
    declare nh_metric int default '0';
    declare path_hash_ids varchar(4096);
    declare path_router_ids varchar(2048);
    declare node_spf_iter int;
    declare node_metric int;

    declare done tinyint;

    declare existing_metric int;
    declare existing_nh_node_hash_id char(32);

    declare loop_count int;
    declare no_more_rows tinyint default FALSE;
    declare no_more_rows_loop1 tinyint default FALSE;

    declare path_cursor CURSOR FOR
        SELECT p.node_hash_id,p.nh_node_hash_id,n.igp_router_id as router_id,p.metric,p.spf_iter
            FROM spf_path p JOIN ls_nodes n ON (p.node_hash_id = n.hash_id
                            and n.peer_hash_id = peer_hash_id)
            ORDER BY spf_iter asc;

    declare nh_cursor CURSOR FOR
        SELECT p.path_hash_ids,p.path_router_ids
            FROM spf_path p WHERE p.node_hash_id = nh_node_hash_id order by spf_iter asc, equal_iter asc;

    declare CONTINUE HANDLER
        FOR NOT FOUND SET no_more_rows = TRUE;

    # Get the node hash id from router ID
    SELECT hash_id INTO node_hash_id FROM ls_nodes
            WHERE ls_nodes.peer_hash_id = peer_hash_id AND
                    igp_router_id = root_router_id and isWithdrawn = False;

    IF (node_hash_id is null) THEN
        SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'ERROR: Invalid router ID';
    END IF;

    set root_node_hash_id = node_hash_id;
    #set igp_rib_table_name = concat(igp_rib_table_name,'_', replace(root_router_id, '.', '_'));
    set igp_rib_table_name = concat(igp_rib_table_name,'_', replace(replace(root_router_id, '.', '_'),':', '_'));

    # Create IGP_RIB table
    set @sql_text = concat("
        create table IF NOT EXISTS ", igp_rib_table_name, " (
            prefix varchar(46) NOT NULL,
            prefix_len int(8) unsigned NOT NULL,
            src_node_hash_id char(32) NOT NULL,
            root_node_hash_id char(32),
            nh_node_hash_id char(32),
            ospf_route_type tinyint NOT NULL,
            metric int(10) not null,
            best tinyint not null DEFAULT FALSE,
            path_hash_ids varchar(4096) NOT NULL default '',
            path_router_ids varchar(2048) NOT NULL default '',
            equal_iter int NOT NULL default '0',
            peer_hash_id char(32) NOT NULL,
            ts timestamp not null default current_timestamp,
            PRIMARY KEY (src_node_hash_id,path_router_ids,prefix,prefix_len)
        ) engine=memory DEFAULT CHARSET=latin1;
    ");

    PREPARE stmt FROM @sql_text;
    EXECUTE stmt;

    #
    # Do not proceed if the current cache is good enough (not too old)
    #
    set @table_age = 9999;
    set @sql_text = concat("
        SELECT (current_timestamp - ts) INTO @table_age  FROM ", igp_rib_table_name,
        "    WHERE peer_hash_id = '", peer_hash_id, "' LIMIT 1");
    PREPARE stmt FROM @sql_text;
    EXECUTE stmt;

    if (@table_age > max_table_age) THEN

        # Create IGP_RIB table
        drop temporary table if exists igp_rib;
        create temporary table igp_rib (
            prefix varchar(46) NOT NULL,
            prefix_len int(8) unsigned NOT NULL,
            src_node_hash_id char(32) NOT NULL,
            ospf_route_type tinyint NOT NULL,
            metric int(10) not null,
            best tinyint not null DEFAULT FALSE,
            PRIMARY KEY (src_node_hash_id,prefix,prefix_len)
        ) engine=memory DEFAULT CHARSET=latin1;

        # Insert all posible prefixes for the rib, includes duplicates which will be removed based on spf
        #   Exclude any external OSPF types
        INSERT IGNORE INTO igp_rib (prefix,prefix_len,src_node_hash_id,ospf_route_type,metric)
             SELECT prefix,prefix_len,localn.hash_id,
                    if (ospf_route_type = 'Intra', 0, if(ospf_route_type = 'Inter', 1,
                        if (ospf_route_type = 'Ext-1', 2, if (ospf_route_type = 'Ext-2', 3, 4)))),
                    metric
                FROM ls_prefixes lp JOIN ls_nodes localn ON (lp.local_node_hash_id = localn.hash_id
                                AND lp.peer_hash_id = localn.peer_hash_id)

                WHERE lp.peer_hash_id = peer_hash_id AND
                    lp.protocol = 'OSPFv2' and lp.isWithdrawn = False;

        # --------
        # Run SPF to get the short path between nodes
        # --------
        set spf_iterations = 1;

        drop temporary table if exists spf_vert;
        create temporary table spf_vert (
            node_hash_id char(32) NOT NULL,
            nh_node_hash_id char(32) NOT NULL,
            metric int(10) not null,
            spf_path_node tinyint not null default '0',
            PRIMARY KEY (node_hash_id,nh_node_hash_id)
        ) engine=memory DEFAULT CHARSET=latin1;

        drop temporary table if exists spf_path;
        create temporary table spf_path (
            node_hash_id char(32) NOT NULL,
            nh_node_hash_id char(32) NOT NULL,
            metric int(10) not null,
            spf_iter int not null  default '9999',
            path_hash_ids varchar(4096) NOT NULL default '',
            path_router_ids varchar(2048) NOT NULL default '',
            equal_iter int NOT NULL default '0',
            PRIMARY KEY (node_hash_id,nh_node_hash_id,equal_iter)
        ) engine=memory DEFAULT CHARSET=latin1;

        # Put root node in table to ensure that it cannot be added by downstream node
        REPLACE INTO spf_path (node_hash_id,nh_node_hash_id,metric,spf_iter)
            VALUES (node_hash_id,node_hash_id,0,spf_iterations);

        #SET nh_node_hash_id = node_hash_id;
        #SET existing_nh_node_hash_id = node_hash_id;

        # --------------
        # LOOP THE BELOW for each node adjacency check
        # --------------
        set done = FALSE;

        WHILE NOT done and spf_iterations <= 1000 DO
            # Get the node list from recently created igp_list
            #TODO: Fix the below to not join ls_nodes so that the metric can be
            #   zeroed or added if the node not a DR
            INSERT IGNORE INTO spf_vert (node_hash_id,nh_node_hash_id,metric)
                SELECT ln.remote_node_hash_id,ln.local_node_hash_id,
                          # Only add the link metric if it's from a non-DR node
                          IF(n.igp_router_id like "%]", nh_metric, (igp_metric + nh_metric))

                    # TODO: Remove the join and replace with a bool column indicating if
                    #    the source node is a DR node or not
                    FROM ls_links ln JOIN ls_nodes n ON (ln.local_node_hash_id = n.hash_id)

                    WHERE ln.local_node_hash_id = node_hash_id and ln.peer_hash_id = peer_hash_id
                         AND ln.isWithdrawn = False
                ON DUPLICATE KEY UPDATE metric=if(values(metric) < metric, values(metric), metric),
                    nh_node_hash_id=if(values(metric) <= metric, values(nh_node_hash_id), nh_node_hash_id);

            # Check if there are any nodes left
            IF EXISTS( SELECT (1) FROM spf_vert WHERE spf_path_node = 0 limit 1) THEN

                # Loop to find the next path node, have to loop because of equal costs
                set loop_count = 0;
                find_next: LOOP

                    IF (loop_count > 1000 or done = 1) THEN
                        LEAVE find_next;
                    ELSE
                        set loop_count = loop_count + 1;
                    END IF;

                    # Check if there are any nodes left
                    IF EXISTS( SELECT (1) FROM spf_vert WHERE spf_path_node = 0 limit 1) THEN
                        SET spf_iterations = spf_iterations + 1;

                        # Determine the lowest cost candidate
                        SELECT s.node_hash_id,s.metric,s.nh_node_hash_id
                                 INTO node_hash_id, nh_metric, nh_node_hash_id
                            FROM spf_vert s WHERE spf_path_node = 0
                            ORDER BY metric asc limit 1;

                        # Get existing path node metric and info (null if not existing)
                        set existing_metric = null;
                        SELECT s.metric,s.nh_node_hash_id INTO existing_metric,existing_nh_node_hash_id
                            FROM spf_path s where s.node_hash_id = node_hash_id
                            ORDER by spf_iter limit 1;

                        IF (existing_metric is null) THEN
                            # add node to path (no others equal at this point)
                            INSERT INTO spf_path (node_hash_id,nh_node_hash_id,metric,spf_iter)
                                    VALUES (node_hash_id,nh_node_hash_id,nh_metric,spf_iterations);

                            UPDATE spf_vert set spf_path_node = 1
                                WHERE spf_vert.node_hash_id = node_hash_id
                                    and spf_vert.nh_node_hash_id = nh_node_hash_id;

                            LEAVE find_next;

                        ELSEIF (existing_metric = nh_metric) THEN
                            # Add equal cost path
                            INSERT IGNORE INTO spf_path (node_hash_id,nh_node_hash_id,metric,spf_iter)
                                    VALUES (node_hash_id,nh_node_hash_id,nh_metric,spf_iterations);

                            UPDATE spf_vert set spf_path_node = 1
                                WHERE spf_vert.node_hash_id = node_hash_id
                                    and spf_vert.nh_node_hash_id = nh_node_hash_id;

                            ITERATE find_next;

                        ELSE
                            # Do not add the metric if the node is a DR/pseudo node connecting to another node.

                            set nh_metric = existing_metric;
                            set nh_node_hash_id = existing_nh_node_hash_id;

                            # There's already another node/path that is better, so this is not needed
                            DELETE FROM spf_vert WHERE spf_path_node = 0 and spf_vert.node_hash_id = node_hash_id;

                            ITERATE find_next;

                        END IF;
                    ELSE
                        set done = TRUE;
                        LEAVE find_next;
                    END IF;

                END LOOP find_next;
            ELSE
                set done = TRUE;
            END IF;

        END WHILE;

        # Update igp rib metric
        UPDATE igp_rib r,spf_path v SET r.metric = IF (r.src_node_hash_id != v.nh_node_hash_id,
                                                        (r.metric + v.metric),0)
                WHERE r.src_node_hash_id = v.node_hash_id;

        drop table spf_vert;
        select spf_iterations;

        # --------------
        # LOOP the path table and add the path trace hash_ids and router_ids
        # --------------

        # Loop throgh the spf path table starting from the root (lowest iteration)
        # The next hop for the current node should have already been processed
        # Next hop calucation is done progressively based on the next hop
        # of the previous.  For this reason, the order of this loop should
        # be ascending order of spf_iterations.
        set no_more_rows = FALSE;
        set done = FALSE;

        OPEN path_cursor;
        WHILE NOT done DO
            FETCH path_cursor INTO node_hash_id,nh_node_hash_id,node_router_id,node_metric,node_spf_iter;

            IF no_more_rows THEN
                set no_more_rows = FALSE;
                set done = TRUE;

            ELSE
                set path_hash_ids = '';
                set path_router_ids = '';

                IF (node_hash_id = nh_node_hash_id) THEN
                    # ROOT NODE
                    UPDATE spf_path p SET p.path_hash_ids = node_hash_id,
                            p.path_router_ids = node_router_id
                        WHERE p.node_hash_id = node_hash_id and
                          p.nh_node_hash_id = nh_node_hash_id and equal_iter = 0;
                ELSE
                    OPEN nh_cursor;
                    set loop_count = 0;

                    ecmp_loop: LOOP
                        # Non ROOT node, need to merge next hop path info
                        FETCH nh_cursor INTO path_hash_ids,path_router_ids;

                        IF no_more_rows THEN
                            set no_more_rows = FALSE;
                            LEAVE ecmp_loop;
                        END IF;

                        if (loop_count = 0) THEN

                            IF (node_router_id not like "%]") THEN
                              UPDATE spf_path p
                                  SET p.path_hash_ids =
                                              concat(path_hash_ids,',',node_hash_id),
                                      p.path_router_ids =
                                              concat(path_router_ids,',', node_router_id)
                                  WHERE p.node_hash_id = node_hash_id and
                                      p.nh_node_hash_id = nh_node_hash_id;
                            ELSE
                              UPDATE spf_path p
                                  SET p.path_hash_ids = path_hash_ids,
                                      p.path_router_ids = path_router_ids
                                  WHERE p.node_hash_id = node_hash_id and
                                      p.nh_node_hash_id = nh_node_hash_id;

                           END IF;

                        ELSE
                            IF node_metric != 0 THEN
                              # Duplicate existing and add new path info
                              IF (node_router_id not like "%]") THEN
                                # Append only if not a DR node
                                INSERT IGNORE INTO spf_path (node_hash_id,nh_node_hash_id,
                                            path_router_ids,path_hash_ids,metric,spf_iter,equal_iter)
                                        VALUES (node_hash_id,nh_node_hash_id,
                                                concat(path_router_ids,',', node_router_id),
                                                concat(path_hash_ids,',',node_hash_id),
                                                node_metric,node_spf_iter,loop_count);
                              ELSE
                                # Must be a DR node, do not append the DR node info
                                INSERT IGNORE INTO spf_path (node_hash_id,nh_node_hash_id,
                                          path_router_ids,path_hash_ids,metric,spf_iter,equal_iter)
                                      VALUES (node_hash_id,nh_node_hash_id,
                                              path_router_ids, path_hash_ids,
                                              node_metric,node_spf_iter,loop_count);

                              END IF;
                            END IF;
                        END IF;

                        set loop_count = loop_count + 1;

                   END LOOP ecmp_loop;
                   CLOSE nh_cursor;
                END IF;
            END IF;
        END WHILE;
        CLOSE path_cursor;

        # ----------------------------
        # Perform best path selection
        # ----------------------------
        # Create IGP_RIB table
        drop temporary table if exists igp_rib_best;
        create temporary table igp_rib_best (
            prefix varchar(46) NOT NULL,
            prefix_len int(8) unsigned NOT NULL,
            metric int(10) not null,
            PRIMARY KEY (prefix,prefix_len)
        ) engine=memory;

        # First put the lowest cost prefixes into table of it's own
        INSERT INTO igp_rib_best (prefix,prefix_len,metric)
            SELECT r.prefix,r.prefix_len,min(r.metric)
                FROM igp_rib r JOIN ls_nodes n ON (r.src_node_hash_id = n.hash_id)
                    JOIN spf_path p ON (r.src_node_hash_id = p.node_hash_id)
                GROUP BY r.prefix,r.prefix_len;

        # Update igp_rib table with the best/lowest metric paths identified
        UPDATE igp_rib r JOIN igp_rib_best b
            SET r.best = TRUE
            WHERE r.prefix = b.prefix and r.prefix_len = b.prefix_len and r.metric = b.metric;

        drop table igp_rib_best;

        # Truncate the igp table for the final result
        set @sql_text = concat("truncate ", igp_rib_table_name, ";");
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;

        # build the final IGP RIB table with neighbors direct neighbors identified
        set @sql_text = concat("
            INSERT IGNORE INTO ", igp_rib_table_name, " (prefix,prefix_len,src_node_hash_id,
                        ospf_route_type,metric,best,path_hash_ids,path_router_ids,
                        equal_iter,nh_node_hash_id,root_node_hash_id, peer_hash_id)
                SELECT
                        t.prefix,t.prefix_len,t.src_node_hash_id,t.ospf_route_type,t.metric,
                        t.best,p.path_hash_ids,p.path_router_ids,p.equal_iter,
                        substring_index(substring_index(p.path_hash_ids,',', 2),',', -1), '",
                        root_node_hash_id, "','", peer_hash_id, "'
                    FROM igp_rib t JOIN spf_path p ON (t.src_node_hash_id = p.node_hash_id);
        ");
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;

        set @sql_text = '';
        set @igp_rib = igp_rib_table_name;

        drop table spf_path;
        drop table igp_rib;

    END IF; # End of if (age is too old or wrong peer)
END
//
delimiter ;
