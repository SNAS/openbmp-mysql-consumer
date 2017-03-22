-- MySQL dump 10.16  Distrib 10.1.21-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: localhost
-- ------------------------------------------------------
-- Server version       10.1.21-MariaDB-1~trusty

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `proc`
--
-- WHERE:  db='openBMP' AND type='PROCEDURE' AND name IN ('DeleteRouter')

LOCK TABLES `proc` WRITE;
/*!40000 ALTER TABLE `proc` DISABLE KEYS */;
INSERT INTO `proc` VALUES ('openBMP','DeleteRouter','PROCEDURE','DeleteRouter','SQL','CONTAINS_SQL','NO','DEFINER','IN rname varchar(200)','','BEGIN\n  DECLARE hid char(32);\n  SELECT hash_id INTO hid FROM routers WHERE name = rname;\n  DELETE r FROM rib r JOIN bgp_peers p ON (r.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;\n  DELETE a FROM path_attrs a JOIN bgp_peers p ON (a.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;\n  DELETE FROM bgp_peers WHERE router_hash_id = hid;\n  DELETE FROM routers WHERE hash_id = hid;\nEND','root@localhost','2017-03-22 13:25:15','2017-03-22 13:25:15','STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION','','latin1','latin1_swedish_ci','latin1_swedish_ci','BEGIN\n  DECLARE hid char(32);\n  SELECT hash_id INTO hid FROM routers WHERE name = rname;\n  DELETE r FROM rib r JOIN bgp_peers p ON (r.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;\n  DELETE a FROM path_attrs a JOIN bgp_peers p ON (a.peer_hash_id = p.hash_id) WHERE p.router_hash_id = hid;\n  DELETE FROM bgp_peers WHERE router_hash_id = hid;\n  DELETE FROM routers WHERE hash_id = hid;\nEND');
/*!40000 ALTER TABLE `proc` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2017-03-22 19:54:52
