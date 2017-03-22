DELIMITER //
DROP PROCEDURE IF EXISTS DeleteRouter;
CREATE PROCEDURE DeleteRouter
(IN rname varchar(200))
BEGIN
  DECLARE hid char(32);
  SELECT hash_id INTO hid FROM routers WHERE name = rname;
  DELETE r FROM rib r JOIN bgp_peers p ON (r.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;
  DELETE a FROM path_attrs a JOIN bgp_peers p ON (a.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;
  DELETE FROM bgp_peers WHERE router_hash_id = hid;
  DELETE FROM routers WHERE hash_id = hid;
END //
DELIMITER ;
