# -----------------------------------------------------------------------------
# Copyright (c) 2015-2017 Cisco Systems, Inc. and others.  All rights reserved.
#
# IS-IS SPF MySQL stored procedure:
#
# Syntax: call spf_isis(<peer hash id>, <root router id>,
#                          <mt id>, <max table age in seconds>, @spf_iterations);
# Details:
#   Will create/update new memory table named 'igp_isis_<peer_hash_id>'
#   with the IGP routing table information.  The 'best' boolean indicates if
#   the prefix is the lowest cost/selected prefix.
# -----------------------------------------------------------------------------
drop procedure if exists spf_isis;
DELIMITER //
CREATE PROCEDURE spf_isis (IN peer_hash_id char(32),
                           IN root_node_hash_id varchar(46),
                           IN input_mt_id int unsigned,
                           IN max_table_age int,
                           OUT spf_iterations int)
BEGIN
    declare igp_rib_table_name varchar(64) default 'igp_isis';
    declare node_hash_id char(32);
    declare node_router_id char(46);
    declare node_router_name char(255);
    declare root_node_router_id char(46);
    declare nh_node_hash_id char(32);
    declare nh_metric int default '0';
    declare path_node_hash_id char(32);
    declare path_hash_ids varchar(4096);
    declare path_router_ids varchar(2048);
    declare path_router_names varchar(4096);
    declare node_spf_iter int;
    declare node_metric int;

    declare done tinyint;

    declare existing_metric int;
    declare existing_nh_node_hash_id char(32);

    declare loop_count int;
    declare no_more_rows tinyint default FALSE;

    declare path_cursor CURSOR FOR
        SELECT p.node_hash_id,p.nh_node_hash_id,nn.igp_router_id,nn.hash_id,nn.name,
            p.metric,p.spf_iter
        FROM spf_path p JOIN ls_nodes n ON (p.node_hash_id = n.hash_id
                                            and n.peer_hash_id = peer_hash_id)
            JOIN (select router_id,concat(left(igp_router_id,14), '.0000') as igp_router_id,
                      bgp_ls_id,hash_id,name
                  -- FROM ls_nodes WHERE igp_router_id like '%.0000') nn
                  FROM ls_nodes
                    WHERE ls_nodes.peer_hash_id = peer_hash_id and igp_router_id like '%.0000') nn
                ON (concat(left(n.igp_router_id, 14), '.0000') = nn.igp_router_id
                    AND nn.bgp_ls_id = n.bgp_ls_id)
        ORDER BY spf_iter asc;

#     declare path_cursor CURSOR FOR
#         SELECT p.node_hash_id,p.nh_node_hash_id,concat(left(igp_router_id,14), '.0000') as igp_router_id,
#                     p.metric,p.spf_iter
#             FROM spf_path p JOIN ls_nodes n ON (p.node_hash_id = n.hash_id
#                             and n.peer_hash_id = peer_hash_id)
#             ORDER BY spf_iter asc;

    declare nh_cursor CURSOR FOR
        SELECT p.path_hash_ids,p.path_router_ids,p.path_router_names
            FROM spf_path p WHERE p.node_hash_id = nh_node_hash_id
                ORDER BY spf_iter,equal_iter asc;

    declare CONTINUE HANDLER
        FOR NOT FOUND SET no_more_rows = TRUE;

    # Set starting node hash id at root
    set node_hash_id = root_node_hash_id;
    set igp_rib_table_name = concat(igp_rib_table_name,'_', input_mt_id,'_', peer_hash_id);

    # Create IGP_RIB table
    set @sql_text = concat(
        "create table IF NOT EXISTS ", igp_rib_table_name, " (
            prefix_bin varbinary(16) NOT NULL,
            prefix_len int(8) unsigned NOT NULL,
            src_node_hash_id char(32) NOT NULL,
            root_node_hash_id char(32) NOT NULL,
            nh_node_hash_id char(32),
            isis_type tinyint NOT NULL,
            metric int(10) unsigned not null,
            best bit(1) not null DEFAULT FALSE,
            path_router_ids varchar(2048) NOT NULL,
            path_hash_ids varchar(4096) NOT NULL default '',
            path_router_names varchar(4096) NOT NULL default '',
            equal_iter int NOT NULL default '0',
            peer_hash_id char(32) NOT NULL,
            mt_id int unsigned,
            ts timestamp not null default current_timestamp,
            PRIMARY KEY (root_node_hash_id,src_node_hash_id,path_router_ids,prefix_bin,prefix_len,mt_id),
            KEY idx_prefix_len (root_node_hash_id, prefix_bin,prefix_len),
            KEY idx_mt_id (mt_id)
        ) engine=innodb DEFAULT CHARSET=latin1 ROW_FORMAT=DYNAMIC;
    ");

    PREPARE stmt FROM @sql_text;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

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
         IF input_mt_id is null THEN
                set input_mt_id = 0;
            END IF;
        # Create IGP_RIB table
        drop temporary table if exists igp_rib;
        create temporary table igp_rib (
            prefix varchar(46) NOT NULL,
            prefix_len int(8) unsigned NOT NULL,
            src_node_hash_id char(32) NOT NULL,
            isis_type tinyint NOT NULL,
            metric int(10) not null,
            best tinyint not null DEFAULT FALSE,
            PRIMARY KEY (src_node_hash_id,prefix,prefix_len),
            KEY idx_src_hash_id (src_node_hash_id)
        ) engine=memory DEFAULT CHARSET=latin1;


        # MT-ID 0 is for standard/single topology
        # MT-ID 2 is for IPv6 topology
        # MT-ID 3 is for multicast/RPF, which is not computed here since unicast routing is the focus
        #
        # See rfc5120 section 7.5 for more details

        # Insert all posible prefixes for the rib, includes duplicates which will be removed based on spf
        INSERT IGNORE INTO igp_rib (prefix,prefix_len,src_node_hash_id,isis_type,metric)
             SELECT prefix,prefix_len,localn.hash_id,if (lp.protocol = 'IS-IS_L1', 1, 2),
                      metric
                FROM ls_prefixes lp JOIN ls_nodes localn ON (lp.local_node_hash_id = localn.hash_id
                                AND lp.peer_hash_id = localn.peer_hash_id)
                WHERE lp.peer_hash_id = peer_hash_id
                    AND lp.protocol IN ('IS-IS_L1', 'IS-IS_L2')
                    AND lp.mt_id= input_mt_id
                    AND lp.isWithdrawn = False;

        # --------
        # Run SPF to get the short path between nodes
        # --------
        set spf_iterations = 1;

        drop temporary table if exists spf_vert;
        create temporary table spf_vert (
            node_hash_id char(32) NOT NULL,
            nh_node_hash_id char(32) NOT NULL,
            metric int(10) unsigned not null,
            spf_path_node tinyint not null default '0',
            PRIMARY KEY (node_hash_id,nh_node_hash_id)
        ) engine=memory DEFAULT CHARSET=latin1;

        drop temporary table if exists spf_path;
        create temporary table spf_path (
            node_hash_id char(32) NOT NULL,
            nh_node_hash_id char(32) NOT NULL,
            metric int(10) unsigned not null,
            spf_iter int not null  default '9999',
            path_hash_ids varchar(4096) NOT NULL default '',
            path_router_ids varchar(4096) NOT NULL default '',
            path_router_names varchar(4096) NOT NULL default '',
            equal_iter int NOT NULL default '0',
            PRIMARY KEY (node_hash_id,nh_node_hash_id,equal_iter)
        ) engine=memory DEFAULT CHARSET=latin1;

       # Put root node in table to ensure that it cannot be added by downstream node
        REPLACE INTO spf_path (node_hash_id,nh_node_hash_id,metric,spf_iter)
            VALUES (node_hash_id,node_hash_id,0,1);

        SET nh_node_hash_id = node_hash_id;

        IF input_mt_id is null THEN
            set input_mt_id = 0;
        END IF;

        # --------------
        # LOOP THE BELOW for each node adjacency check
        # --------------
        set done = FALSE;

        WHILE NOT done and spf_iterations <= 1000 DO
            # Get the node list from recently created igp_list
            INSERT IGNORE INTO spf_vert (node_hash_id,nh_node_hash_id,metric)
                SELECT ln.remote_node_hash_id,ln.local_node_hash_id,(igp_metric + nh_metric)
                    FROM ls_links ln
                    WHERE ln.local_node_hash_id = node_hash_id AND ln.peer_hash_id = peer_hash_id
                        AND ln.mt_id = input_mt_id
                        AND ln.isWithdrawn = False

                ON DUPLICATE KEY UPDATE metric=if(values(metric) < metric, values(metric), metric),
                    nh_node_hash_id=if(values(metric) < metric, values(nh_node_hash_id), nh_node_hash_id);

            # Check if there are any nodes left
            IF EXISTS( SELECT (1) FROM spf_vert WHERE spf_path_node = 0 limit 1) THEN

                # Loop to find the next path node have to loop because of equal costs
                set loop_count = 0;
                find_next: LOOP

                    IF (loop_count > 100 or done = 1) THEN
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

        # --------------
        # LOOP the path table and add the path trace hash_ids and router_ids
        # --------------

        # Loop through the spf path table starting from the root (lowest iteration)
        # The next hop for the current node should have already been processed
        set no_more_rows = FALSE;
        set done = FALSE;

        OPEN path_cursor;
        WHILE NOT done DO
            FETCH path_cursor INTO node_hash_id,nh_node_hash_id,node_router_id,path_node_hash_id,node_router_name,node_metric,node_spf_iter;

            IF no_more_rows THEN
                set no_more_rows = FALSE;
                set done = TRUE;

            ELSE
                set path_hash_ids = '';
                set path_router_ids = '';
                set path_router_names = '';

                IF (node_hash_id = nh_node_hash_id) THEN
                    # ROOT NODE
                    UPDATE spf_path p SET
                        p.path_hash_ids = path_node_hash_id,
                        p.path_router_ids = node_router_id,
                        p.path_router_names = node_router_name
                    WHERE p.node_hash_id = node_hash_id and
                          p.nh_node_hash_id = nh_node_hash_id and equal_iter = 0;

                    set root_node_router_id = node_router_id;
                ELSE
                    OPEN nh_cursor;
                    set loop_count = 0;

                    ecmp_loop: LOOP
                        # Non ROOT node, need to merge next hop path info
                        FETCH nh_cursor INTO path_hash_ids,path_router_ids,path_router_names;

                        IF no_more_rows THEN
                            set no_more_rows = FALSE;
                            LEAVE ecmp_loop;
                        END IF;

                        IF loop_count = 0 THEN
                            # Update the table with the previous path_hash_ids/router_ids with the new one
                            #    Pseudo nodes result in duplicates, so here we do not append/concat the
                            #    path_node_hash_id/router_id again.
                            IF path_hash_ids NOT LIKE concat('%,', path_node_hash_id) THEN
                                UPDATE spf_path p
                                SET p.path_hash_ids = concat(path_hash_ids,',',path_node_hash_id),
                                    p.path_router_ids = concat(path_router_ids,',', node_router_id),
                                    p.path_router_names = concat(path_router_names,',', node_router_name)
                                WHERE p.node_hash_id = node_hash_id AND
                                      p.nh_node_hash_id = nh_node_hash_id and equal_iter = 0;

                            ELSE
                                # Update only, no append
                                UPDATE spf_path p
                                SET p.path_hash_ids = path_hash_ids,
                                    p.path_router_ids = path_router_ids,
                                    p.path_router_names = path_router_names
                                WHERE p.node_hash_id = node_hash_id AND
                                      p.nh_node_hash_id = nh_node_hash_id and equal_iter = 0;
                            END IF;
                        ELSE
                            IF node_metric != 0 THEN
                                # Duplicate existing and add new path info
                                INSERT IGNORE INTO spf_path (node_hash_id,nh_node_hash_id,
                                                             path_router_ids,path_hash_ids,path_router_names,
                                                             metric,spf_iter,equal_iter)
                                VALUES (node_hash_id,nh_node_hash_id,
                                        concat(path_router_ids,',', node_router_id),
                                        concat(path_hash_ids,',',path_node_hash_id),
                                        concat(path_router_names,',',node_router_name),
                                        node_metric,node_spf_iter,loop_count);
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
        # Truncate the igp table for the final result
        set @sql_text = concat("truncate ", igp_rib_table_name, ";");
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;

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

        # build the final IGP RIB table with direct neighbors identified
        set @sql_text = concat("
            REPLACE INTO ", igp_rib_table_name, " (prefix_bin,prefix_len,src_node_hash_id,
                        isis_type,metric,best,path_router_ids,path_router_names,path_hash_ids,
                        equal_iter,nh_node_hash_id,root_node_hash_id,peer_hash_id,mt_id)
                SELECT
                        inet6_aton(t.prefix),t.prefix_len,t.src_node_hash_id,t.isis_type,t.metric,
                        t.best,p.path_router_ids,p.path_router_names,p.path_hash_ids,
                        p.equal_iter,
                        substring_index(substring_index(p.path_hash_ids,',', 2),',', -1), '",
                        root_node_hash_id, "','", peer_hash_id, "',",input_mt_id, "
                    FROM igp_rib t JOIN spf_path p ON (t.src_node_hash_id = p.node_hash_id);
        ");
        PREPARE stmt FROM @sql_text;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        set @sql_text = '';
        set @igp_rib = igp_rib_table_name;

        #drop table spf_path;
        drop table igp_rib;

    END IF; # End of if (age is too old or wrong peer)
END
//
delimiter ;
