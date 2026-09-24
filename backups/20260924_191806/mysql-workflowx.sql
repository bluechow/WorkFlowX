-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: workflowx
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `attachments`
--

DROP TABLE IF EXISTS `attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attachments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `issue_id` bigint unsigned NOT NULL COMMENT '所属 Issue',
  `object_key` varchar(200) NOT NULL COMMENT 'MinIO 对象键（服务端生成）',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名（仅元数据，不参与对象键）',
  `file_size` bigint unsigned NOT NULL COMMENT '字节数',
  `content_type` varchar(100) NOT NULL COMMENT '客户端声明的 MIME 类型（不作为放行依据）',
  `uploader_id` bigint unsigned NOT NULL COMMENT '上传者（逻辑引用 users.id）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attachments_object_key` (`object_key`),
  KEY `idx_attachments_issue` (`issue_id`,`created_at`),
  KEY `idx_attachments_uploader` (`uploader_id`),
  CONSTRAINT `fk_attachments_issue` FOREIGN KEY (`issue_id`) REFERENCES `issues` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue 附件（元数据）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `attachments`
--

LOCK TABLES `attachments` WRITE;
/*!40000 ALTER TABLE `attachments` DISABLE KEYS */;
/*!40000 ALTER TABLE `attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '操作人（匿名请求为 NULL，逻辑引用 users.id）',
  `module` varchar(30) NOT NULL COMMENT '业务模块（AUTH/USER/ORG/PROJECT/ISSUE/COMMENT/ATTACHMENT）',
  `action` varchar(30) NOT NULL COMMENT '操作（LOGIN/LOGIN_FAIL/LOGOUT/CREATE/UPDATE/DELETE/STATUS/TRANSITION/ASSIGN_MEMBER/UPLOAD）',
  `http_method` varchar(10) NOT NULL COMMENT 'HTTP Method',
  `uri` varchar(200) NOT NULL COMMENT '请求 URI',
  `ip` varchar(45) NOT NULL COMMENT '客户端 IP（兼容 IPv6）',
  `target` varchar(100) DEFAULT NULL COMMENT '目标对象（如 issue:123）',
  `summary` varchar(500) DEFAULT NULL COMMENT '业务摘要（白名单字段组装，不含敏感数据）',
  `success` tinyint(1) NOT NULL COMMENT '操作结果',
  `trace_id` varchar(32) DEFAULT NULL COMMENT '链路 ID（与日志/响应头一致）',
  `user_agent` varchar(255) DEFAULT NULL COMMENT 'User-Agent',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_user_time` (`user_id`,`created_at`),
  KEY `idx_audit_module` (`module`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志（高价值操作事实）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES (1,1,'AUTH','LOGIN','POST','/api/v1/auth/login','172.19.0.1','user:1','登录成功',1,'81bda0bb497341b99af3729f64753287','curl/8.14.1','2026-09-24 19:04:14.868'),(2,1,'ORG','CREATE','POST','/api/v1/orgs','172.19.0.1','org:1','创建组织 SMOKEORG1790247855',1,'819928a371cf42479d2923eb9ce8c4fd','curl/8.14.1','2026-09-24 19:04:15.637'),(3,1,'PROJECT','CREATE','POST','/api/v1/projects','172.19.0.1','project:1','创建项目 SMOKEP1790247855',1,'ab42c70a365143eb83864188877125c2','curl/8.14.1','2026-09-24 19:04:16.033'),(4,1,'PROJECT','ASSIGN_MEMBER','POST','/api/v1/projects/1/members','172.19.0.1','project:1','项目成员 2 角色设为 MEMBER',1,'c62b3d7a07754f4891e99729b53a8712','curl/8.14.1','2026-09-24 19:04:16.632'),(5,1,'ISSUE','CREATE','POST','/api/v1/projects/1/issues','172.19.0.1','issue:1','创建 Issue SMOKEP1790247855-1 SMOKE issue',1,'7cca518e33244d5887b4a96d30d7583b','curl/8.14.1','2026-09-24 19:04:16.813'),(6,1,'ISSUE','TRANSITION','PATCH','/api/v1/projects/1/issues/1/status','172.19.0.1','issue:1','状态流转 OPEN -> IN_PROGRESS',1,'335568f1a0634eb482b22f0a9ab754f7','curl/8.14.1','2026-09-24 19:04:17.250'),(7,1,'COMMENT','CREATE','POST','/api/v1/projects/1/issues/1/comments','172.19.0.1','issue:1','评论 Issue 1',1,'9d6c50f75e69450caaec345fcb38fcbf','curl/8.14.1','2026-09-24 19:04:17.417'),(8,1,'AUTH','LOGIN','POST','/api/v1/auth/login','172.19.0.1','user:1','登录成功',1,'9a03767c9b2f40ab80bde47fa4b60c4f','python-httpx/0.28.1','2026-09-24 19:04:42.840'),(9,1,'ORG','CREATE','POST','/api/v1/orgs','172.19.0.1','org:2','创建组织 SMK2ORG1790247882',1,'8ebc2c6d12264311a59a222f2770c3bc','python-httpx/0.28.1','2026-09-24 19:04:42.871'),(10,1,'PROJECT','CREATE','POST','/api/v1/projects','172.19.0.1','project:2','创建项目 SMK2P1790247882',1,'5267c5a698ea43cd9f12713999862f70','python-httpx/0.28.1','2026-09-24 19:04:42.918'),(11,1,'ISSUE','CREATE','POST','/api/v1/projects/2/issues','172.19.0.1','issue:2','创建 Issue SMK2P1790247882-1 smoke2',1,'6ca8672c1eea4d3d9e064224514764d8','python-httpx/0.28.1','2026-09-24 19:04:42.967'),(12,1,'ATTACHMENT','UPLOAD','POST','/api/v1/projects/2/issues/2/attachments','172.19.0.1','issue:2','上传附件 smoke.txt（4B）',1,'764732d0b76b450aa63030317824fab8','python-httpx/0.28.1','2026-09-24 19:04:43.057'),(13,1,'ORG','DELETE','DELETE','/api/v1/orgs/2','172.19.0.1','org:2','删除组织 SMK2ORG1790247882',1,'af0b8714168d46f3a075a189f850b093','python-httpx/0.28.1','2026-09-24 19:04:43.099'),(14,1,'AUTH','LOGIN','POST','/api/v1/auth/login','172.19.0.1','user:1','登录成功',1,'1b5adf84ef8a4503a137de52ddd14f77','curl/8.14.1','2026-09-24 19:07:16.431'),(15,1,'ORG','CREATE','POST','/api/v1/orgs','172.19.0.1','org:3','创建组织 SMOKEORG1790248036',1,'c1e85bf7792244d3b1bd90291b2c63e1','curl/8.14.1','2026-09-24 19:07:17.071'),(16,1,'PROJECT','CREATE','POST','/api/v1/projects','172.19.0.1','project:3','创建项目 SMOKEP1790248036',1,'af388543d6294271aca3e46885162909','curl/8.14.1','2026-09-24 19:07:17.511'),(17,1,'PROJECT','ASSIGN_MEMBER','POST','/api/v1/projects/3/members','172.19.0.1','project:3','项目成员 2 角色设为 MEMBER',1,'b4cec7cb83e1404c9a2c76e89d0d39d5','curl/8.14.1','2026-09-24 19:07:18.039'),(18,1,'ISSUE','CREATE','POST','/api/v1/projects/3/issues','172.19.0.1','issue:3','创建 Issue SMOKEP1790248036-1 SMOKE issue',1,'a607aabe84954ee79ce9fa2d0faa50a8','curl/8.14.1','2026-09-24 19:07:18.194'),(19,1,'ISSUE','TRANSITION','PATCH','/api/v1/projects/3/issues/3/status','172.19.0.1','issue:3','状态流转 OPEN -> IN_PROGRESS',1,'4fb4f60185a1470ca3c55b291f4b5c5a','curl/8.14.1','2026-09-24 19:07:18.606'),(20,1,'COMMENT','CREATE','POST','/api/v1/projects/3/issues/3/comments','172.19.0.1','issue:3','评论 Issue 1',1,'96c32bafe2b44b22b587acd230d4a2e4','curl/8.14.1','2026-09-24 19:07:18.766'),(21,1,'ATTACHMENT','UPLOAD','POST','/api/v1/projects/3/issues/3/attachments','172.19.0.1','issue:3','上传附件 .smoke-att.txt（24B）',1,'1a5e36092e274e7eb86dd3a4d557de3c','curl/8.14.1','2026-09-24 19:07:18.941'),(22,1,'ATTACHMENT','DELETE','DELETE','/api/v1/projects/3/issues/3/attachments/2','172.19.0.1','issue:3','删除附件 .smoke-att.txt',1,'1f3cc6d16fc64cfa82ad4a9904540756','curl/8.14.1','2026-09-24 19:07:19.703'),(23,1,'ORG','DELETE','DELETE','/api/v1/orgs/3','172.19.0.1','org:3','删除组织 SMOKEORG1790248036',1,'42ad13baadfe422399ab92d27a31dea1','curl/8.14.1','2026-09-24 19:07:20.356');
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `departments`
--

DROP TABLE IF EXISTS `departments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `departments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id` bigint unsigned NOT NULL COMMENT '所属组织',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父部门 ID（NULL=根部门，自引用）',
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `code` varchar(50) NOT NULL COMMENT '部门编码（组织内唯一）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_departments_org_code` (`org_id`,`code`),
  KEY `idx_departments_org` (`org_id`),
  KEY `idx_departments_parent` (`parent_id`),
  CONSTRAINT `fk_departments_org` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_departments_parent` FOREIGN KEY (`parent_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门（组织内两级以上树）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `departments`
--

LOCK TABLES `departments` WRITE;
/*!40000 ALTER TABLE `departments` DISABLE KEYS */;
/*!40000 ALTER TABLE `departments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','identity core','SQL','V1__identity_core.sql',-1049456380,'workflowx','2026-09-24 10:55:29',247,1),(2,'2','seed dev','SQL','V2__seed_dev.sql',678390009,'workflowx','2026-09-24 10:55:29',12,1),(3,'3','rbac permissions','SQL','V3__rbac_permissions.sql',1499539497,'workflowx','2026-09-24 10:55:29',16,1),(4,'4','organization core','SQL','V4__organization_core.sql',-2031717326,'workflowx','2026-09-24 10:55:29',226,1),(5,'5','org permissions','SQL','V5__org_permissions.sql',-456510388,'workflowx','2026-09-24 10:55:29',12,1),(6,'6','project core','SQL','V6__project_core.sql',2071146264,'workflowx','2026-09-24 10:55:29',97,1),(7,'7','project permissions','SQL','V7__project_permissions.sql',-1444039725,'workflowx','2026-09-24 10:55:29',6,1),(8,'8','project members','SQL','V8__project_members.sql',-732367062,'workflowx','2026-09-24 10:55:29',49,1),(9,'9','issue core','SQL','V9__issue_core.sql',1962740538,'workflowx','2026-09-24 10:55:30',186,1),(10,'10','issue permissions','SQL','V10__issue_permissions.sql',-1084789406,'workflowx','2026-09-24 10:55:30',5,1),(11,'11','workflow permissions','SQL','V11__workflow_permissions.sql',1501632809,'workflowx','2026-09-24 10:55:30',3,1),(12,'12','comment attachment core','SQL','V12__comment_attachment_core.sql',-224448312,'workflowx','2026-09-24 10:55:30',125,1),(13,'13','notification core','SQL','V13__notification_core.sql',-1056415347,'workflowx','2026-09-24 10:55:30',66,1),(14,'14','audit dashboard core','SQL','V14__audit_dashboard_core.sql',-1896962831,'workflowx','2026-09-24 10:55:30',69,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue_comments`
--

DROP TABLE IF EXISTS `issue_comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `issue_id` bigint unsigned NOT NULL COMMENT '所属 Issue',
  `author_id` bigint unsigned NOT NULL COMMENT '作者（创建者，逻辑引用 users.id）',
  `content` text NOT NULL COMMENT '内容',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_comments_issue` (`issue_id`,`created_at`),
  KEY `idx_comments_author` (`author_id`),
  CONSTRAINT `fk_comments_issue` FOREIGN KEY (`issue_id`) REFERENCES `issues` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue 评论';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue_comments`
--

LOCK TABLES `issue_comments` WRITE;
/*!40000 ALTER TABLE `issue_comments` DISABLE KEYS */;
INSERT INTO `issue_comments` VALUES (1,1,1,'SMOKE comment','2026-09-24 19:04:17.399','2026-09-24 19:04:17.399');
/*!40000 ALTER TABLE `issue_comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issues`
--

DROP TABLE IF EXISTS `issues`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issues` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint unsigned NOT NULL COMMENT '所属项目',
  `issue_no` bigint unsigned NOT NULL COMMENT '项目内序号（业务编号=project.key-issue_no）',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `description` text COMMENT '描述',
  `type` enum('BUG','TASK','FEATURE','IMPROVEMENT') NOT NULL DEFAULT 'TASK' COMMENT '类型',
  `priority` enum('LOW','MEDIUM','HIGH','URGENT') NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
  `severity` enum('S1','S2','S3','S4') DEFAULT NULL COMMENT 'Bug 严重程度（仅 type=BUG，可空）',
  `status` enum('OPEN','IN_PROGRESS','RESOLVED','TESTING','CLOSED','REOPENED') NOT NULL DEFAULT 'OPEN' COMMENT '状态（流转规则属 Phase 7）',
  `reporter_id` bigint unsigned NOT NULL COMMENT '报告人（创建者，逻辑引用 users.id）',
  `assignee_id` bigint unsigned DEFAULT NULL COMMENT '经办人（须为项目成员，逻辑引用 users.id）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issues_project_no` (`project_id`,`issue_no`),
  KEY `idx_issues_project_status` (`project_id`,`status`),
  KEY `idx_issues_project_type` (`project_id`,`type`),
  KEY `idx_issues_project_priority` (`project_id`,`priority`),
  KEY `idx_issues_assignee` (`assignee_id`),
  KEY `idx_issues_reporter` (`reporter_id`),
  CONSTRAINT `fk_issues_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issues`
--

LOCK TABLES `issues` WRITE;
/*!40000 ALTER TABLE `issues` DISABLE KEYS */;
INSERT INTO `issues` VALUES (1,1,1,'SMOKE issue',NULL,'TASK','HIGH',NULL,'IN_PROGRESS',1,2,'2026-09-24 19:04:16.808','2026-09-24 19:04:17.247');
/*!40000 ALTER TABLE `issues` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` bigint unsigned NOT NULL COMMENT '接收人（逻辑引用 users.id）',
  `type` varchar(30) NOT NULL COMMENT '通知类型（ISSUE_ASSIGNED/ISSUE_STATUS_CHANGED/ISSUE_COMMENTED）',
  `title` varchar(200) NOT NULL COMMENT '标题（真实业务上下文）',
  `content` varchar(1000) DEFAULT NULL COMMENT '内容（业务编号+标题+操作者）',
  `related_type` varchar(30) DEFAULT NULL COMMENT '关联对象类型（ISSUE）',
  `related_id` bigint unsigned DEFAULT NULL COMMENT '关联对象 id（issueId，跳转用）',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '已读标记',
  `read_at` datetime(3) DEFAULT NULL COMMENT '已读时间（与 is_read 同步写）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notifications_recipient` (`recipient_id`,`is_read`),
  KEY `idx_notifications_recipient_created` (`recipient_id`,`created_at`),
  KEY `idx_notifications_related` (`related_type`,`related_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,2,'ISSUE_ASSIGNED','Issue 已分派给您','SMOKEP1790247855-1 SMOKE issue（由 #1 分派）','ISSUE',1,0,NULL,'2026-09-24 19:04:16.817'),(2,2,'ISSUE_STATUS_CHANGED','Issue 状态变更为 IN_PROGRESS','SMOKEP1790247855-1 SMOKE issue（由 #1 流转）','ISSUE',1,0,NULL,'2026-09-24 19:04:17.253'),(3,2,'ISSUE_COMMENTED','Issue 有新评论','SMOKEP1790247855-1 SMOKE issue（#1 发表评论）','ISSUE',1,0,NULL,'2026-09-24 19:04:17.409'),(4,2,'ISSUE_ASSIGNED','Issue 已分派给您','SMOKEP1790248036-1 SMOKE issue（由 #1 分派）','ISSUE',3,0,NULL,'2026-09-24 19:07:18.195'),(5,2,'ISSUE_STATUS_CHANGED','Issue 状态变更为 IN_PROGRESS','SMOKEP1790248036-1 SMOKE issue（由 #1 流转）','ISSUE',3,0,NULL,'2026-09-24 19:07:18.609'),(6,2,'ISSUE_COMMENTED','Issue 有新评论','SMOKEP1790248036-1 SMOKE issue（#1 发表评论）','ISSUE',3,0,NULL,'2026-09-24 19:07:18.758');
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `organization_members`
--

DROP TABLE IF EXISTS `organization_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_members` (
  `org_id` bigint unsigned NOT NULL COMMENT '组织 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '成员用户 ID',
  `role` enum('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '组织内角色',
  `department_id` bigint unsigned DEFAULT NULL COMMENT '所属部门（可空=未分配）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (`org_id`,`user_id`),
  KEY `idx_org_members_user` (`user_id`),
  KEY `idx_org_members_dept` (`department_id`),
  CONSTRAINT `fk_org_members_dept` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_org_members_org` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_org_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织成员（用户-组织归属+部门归属）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `organization_members`
--

LOCK TABLES `organization_members` WRITE;
/*!40000 ALTER TABLE `organization_members` DISABLE KEYS */;
INSERT INTO `organization_members` VALUES (1,1,'OWNER',NULL,'2026-09-24 19:04:15.633'),(1,2,'MEMBER',NULL,'2026-09-24 19:04:16.451');
/*!40000 ALTER TABLE `organization_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `organizations`
--

DROP TABLE IF EXISTS `organizations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organizations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '组织名称',
  `code` varchar(50) NOT NULL COMMENT '组织编码',
  `owner_id` bigint unsigned NOT NULL COMMENT '所有者用户 ID（逻辑引用 users.id，不建外键）',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_organizations_code` (`code`),
  KEY `idx_organizations_owner` (`owner_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `organizations`
--

LOCK TABLES `organizations` WRITE;
/*!40000 ALTER TABLE `organizations` DISABLE KEYS */;
INSERT INTO `organizations` VALUES (1,'SMOKE org','SMOKEORG1790247855',1,NULL,'2026-09-24 19:04:15.629','2026-09-24 19:04:15.629');
/*!40000 ALTER TABLE `organizations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(100) NOT NULL COMMENT '权限编码（resource:action，如 issue:delete）',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `type` enum('MENU','API','BUTTON') NOT NULL DEFAULT 'API' COMMENT '权限类型',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permissions`
--

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
INSERT INTO `permissions` VALUES (1,'user:list','用户列表','API','分页查询用户','2026-09-24 18:55:29.307','2026-09-24 18:55:29.307'),(2,'user:get','用户详情','API','按 ID 查询用户','2026-09-24 18:55:29.308','2026-09-24 18:55:29.308'),(3,'user:create','创建用户','API','管理员创建用户','2026-09-24 18:55:29.310','2026-09-24 18:55:29.310'),(4,'user:update','更新用户','API','管理员更新用户基本信息','2026-09-24 18:55:29.311','2026-09-24 18:55:29.311'),(5,'user:status','用户状态','API','启用/禁用用户（禁用即踢线）','2026-09-24 18:55:29.312','2026-09-24 18:55:29.312'),(6,'user:assign_role','分配用户角色','API','给用户分配/回收角色','2026-09-24 18:55:29.312','2026-09-24 18:55:29.312'),(7,'role:list','角色列表','API','查询全部角色','2026-09-24 18:55:29.313','2026-09-24 18:55:29.313'),(8,'role:get','角色详情','API','查询角色及其权限','2026-09-24 18:55:29.314','2026-09-24 18:55:29.314'),(9,'role:create','创建角色','API','创建新角色','2026-09-24 18:55:29.314','2026-09-24 18:55:29.314'),(10,'role:update','更新角色','API','更新角色名称/描述','2026-09-24 18:55:29.315','2026-09-24 18:55:29.315'),(11,'role:delete','删除角色','API','删除非系统角色','2026-09-24 18:55:29.316','2026-09-24 18:55:29.316'),(12,'role:assign_permission','分配角色权限','API','给角色分配/回收权限','2026-09-24 18:55:29.317','2026-09-24 18:55:29.317'),(13,'permission:list','权限列表','API','查询全部权限','2026-09-24 18:55:29.318','2026-09-24 18:55:29.318'),(14,'permission:get','权限详情','API','按编码查询权限','2026-09-24 18:55:29.318','2026-09-24 18:55:29.318'),(15,'org:list','组织列表','API','分页查询组织','2026-09-24 18:55:29.593','2026-09-24 18:55:29.593'),(16,'org:get','组织详情','API','查询组织详情与成员','2026-09-24 18:55:29.594','2026-09-24 18:55:29.594'),(17,'org:create','创建组织','API','创建新组织（创建者成为 OWNER）','2026-09-24 18:55:29.594','2026-09-24 18:55:29.594'),(18,'org:update','更新组织','API','更新组织基本信息','2026-09-24 18:55:29.595','2026-09-24 18:55:29.595'),(19,'org:delete','删除组织','API','删除组织（数据级叠加：仅组织 OWNER）','2026-09-24 18:55:29.595','2026-09-24 18:55:29.595'),(20,'org:assign_member','组织成员管理','API','添加/移除组织成员','2026-09-24 18:55:29.596','2026-09-24 18:55:29.596'),(21,'department:list','部门列表','API','查询组织内部门','2026-09-24 18:55:29.597','2026-09-24 18:55:29.597'),(22,'department:get','部门详情','API','查询部门详情','2026-09-24 18:55:29.598','2026-09-24 18:55:29.598'),(23,'department:create','创建部门','API','创建部门','2026-09-24 18:55:29.599','2026-09-24 18:55:29.599'),(24,'department:update','更新部门','API','更新部门（含调整父级，防环）','2026-09-24 18:55:29.600','2026-09-24 18:55:29.600'),(25,'department:delete','删除部门','API','删除部门（子级提升为根）','2026-09-24 18:55:29.601','2026-09-24 18:55:29.601'),(26,'project:list','项目列表','API','分页查询项目','2026-09-24 18:55:29.744','2026-09-24 18:55:29.744'),(27,'project:get','项目详情','API','查询项目详情','2026-09-24 18:55:29.745','2026-09-24 18:55:29.745'),(28,'project:create','创建项目','API','创建项目（数据级叠加：须为目标组织成员）','2026-09-24 18:55:29.746','2026-09-24 18:55:29.746'),(29,'project:update','更新项目','API','更新项目/归档恢复（数据级叠加：须为组织成员）','2026-09-24 18:55:29.746','2026-09-24 18:55:29.746'),(30,'project:delete','删除项目','API','预留（当前归档代替物理删除）','2026-09-24 18:55:29.747','2026-09-24 18:55:29.747'),(31,'project:assign_member','项目成员管理','API','添加/移除项目成员','2026-09-24 18:55:29.813','2026-09-24 18:55:29.813'),(32,'issue:list','Issue 列表','API','项目内分页查询 Issue','2026-09-24 18:55:30.038','2026-09-24 18:55:30.038'),(33,'issue:get','Issue 详情','API','查询 Issue 详情','2026-09-24 18:55:30.039','2026-09-24 18:55:30.039'),(34,'issue:create','创建 Issue','API','创建 Issue（数据级叠加：须为项目成员）','2026-09-24 18:55:30.039','2026-09-24 18:55:30.039'),(35,'issue:update','更新 Issue','API','更新 Issue/状态（数据级叠加：须为项目成员）','2026-09-24 18:55:30.040','2026-09-24 18:55:30.040'),(36,'issue:assign','分派 Issue','API','变更 Issue 经办人（数据级叠加：须为项目成员）','2026-09-24 18:55:30.040','2026-09-24 18:55:30.040'),(37,'issue:transition','Issue 状态流转','API','按正式矩阵执行 Issue 状态流转（非法流转 409）','2026-09-24 18:55:30.061','2026-09-24 18:55:30.061'),(38,'comment:list','评论列表','API','查看项目 Issue 的评论列表','2026-09-24 18:55:30.200','2026-09-24 18:55:30.200'),(39,'comment:get','评论详情','API','查看单条评论','2026-09-24 18:55:30.201','2026-09-24 18:55:30.201'),(40,'comment:create','创建评论','API','在项目 Issue 下发表评论','2026-09-24 18:55:30.201','2026-09-24 18:55:30.201'),(41,'comment:update','编辑评论','API','编辑本人发表的评论','2026-09-24 18:55:30.202','2026-09-24 18:55:30.202'),(42,'comment:delete','删除评论','API','删除本人发表的评论','2026-09-24 18:55:30.202','2026-09-24 18:55:30.202'),(43,'attachment:list','附件列表','API','查看项目 Issue 的附件列表','2026-09-24 18:55:30.203','2026-09-24 18:55:30.203'),(44,'attachment:get','附件下载','API','下载项目 Issue 的附件（后端鉴权流式读取）','2026-09-24 18:55:30.203','2026-09-24 18:55:30.203'),(45,'attachment:upload','上传附件','API','向项目 Issue 上传附件（白名单类型与大小限制）','2026-09-24 18:55:30.203','2026-09-24 18:55:30.203'),(46,'attachment:delete','删除附件','API','删除本人上传的附件（MinIO 对象与元数据一并删除）','2026-09-24 18:55:30.204','2026-09-24 18:55:30.204'),(47,'audit:list','审计日志列表','API','查询系统审计日志（高敏感，仅管理角色）','2026-09-24 18:55:30.371','2026-09-24 18:55:30.371'),(48,'audit:get','审计日志详情','API','查看单条审计日志详情','2026-09-24 18:55:30.372','2026-09-24 18:55:30.372'),(49,'dashboard:view','仪表盘查看','API','查看数据统计仪表盘（数据范围按角色/成员关系收敛）','2026-09-24 18:55:30.372','2026-09-24 18:55:30.372');
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_members`
--

DROP TABLE IF EXISTS `project_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_members` (
  `project_id` bigint unsigned NOT NULL COMMENT '项目 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '成员用户 ID',
  `role` enum('OWNER','MANAGER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '项目内角色',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (`project_id`,`user_id`),
  KEY `idx_project_members_user` (`user_id`),
  CONSTRAINT `fk_project_members_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_project_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_members`
--

LOCK TABLES `project_members` WRITE;
/*!40000 ALTER TABLE `project_members` DISABLE KEYS */;
INSERT INTO `project_members` VALUES (1,1,'OWNER','2026-09-24 19:04:16.030'),(1,2,'MEMBER','2026-09-24 19:04:16.628');
/*!40000 ALTER TABLE `project_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `projects`
--

DROP TABLE IF EXISTS `projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projects` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id` bigint unsigned NOT NULL COMMENT '所属组织',
  `key` varchar(20) NOT NULL COMMENT '项目标识（全局唯一，Issue 编号前缀，如 WFX）',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `status` enum('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（归档策略）',
  `owner_id` bigint unsigned NOT NULL COMMENT '创建者/负责人（逻辑引用 users.id）',
  `issue_seq` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Issue 序号计数器（已分配的最大 issue_no）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_projects_key` (`key`),
  KEY `idx_projects_org_status` (`org_id`,`status`),
  KEY `idx_projects_owner` (`owner_id`),
  CONSTRAINT `fk_projects_org` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `projects`
--

LOCK TABLES `projects` WRITE;
/*!40000 ALTER TABLE `projects` DISABLE KEYS */;
INSERT INTO `projects` VALUES (1,1,'SMOKEP1790247855','SMOKE proj',NULL,'ACTIVE',1,1,'2026-09-24 19:04:16.026','2026-09-24 19:04:16.803');
/*!40000 ALTER TABLE `projects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permissions`
--

DROP TABLE IF EXISTS `role_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `role_id` bigint unsigned NOT NULL COMMENT '角色 ID',
  `permission_id` bigint unsigned NOT NULL COMMENT '权限 ID',
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `idx_role_permissions_perm` (`permission_id`),
  CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permissions`
--

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
INSERT INTO `role_permissions` VALUES (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),(1,20),(1,21),(1,22),(1,23),(1,24),(1,25),(1,26),(1,27),(1,28),(1,29),(1,30),(1,31),(1,32),(1,33),(1,34),(1,35),(1,36),(1,37),(1,38),(1,39),(1,40),(1,41),(1,42),(1,43),(1,44),(1,45),(1,46),(1,47),(1,48),(1,49);
/*!40000 ALTER TABLE `role_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(50) NOT NULL COMMENT '角色编码（如 ADMIN/MANAGER/MEMBER）',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'ADMIN','系统管理员','拥有系统全部管理权限','2026-09-24 18:55:29.273','2026-09-24 18:55:29.273'),(2,'MEMBER','普通成员','普通团队成员','2026-09-24 18:55:29.276','2026-09-24 18:55:29.276');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_roles`
--

DROP TABLE IF EXISTS `user_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色 ID',
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `idx_user_roles_role` (`role_id`),
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_roles`
--

LOCK TABLES `user_roles` WRITE;
/*!40000 ALTER TABLE `user_roles` DISABLE KEYS */;
INSERT INTO `user_roles` VALUES (1,1),(2,2);
/*!40000 ALTER TABLE `user_roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '登录名',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `password_hash` varchar(100) NOT NULL COMMENT '密码散列（bcrypt），禁止明文',
  `nickname` varchar(50) DEFAULT NULL COMMENT '显示昵称',
  `status` enum('ACTIVE','DISABLED','LOCKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
  `last_login_at` datetime(3) DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','admin@workflowx.local','$2a$10$hB88hOHWdfiY9DMVNfQMUui7Nyyf8BVg6rEzYCOgdVlo6s.BJjnBW','系统管理员','ACTIVE','2026-09-24 19:07:16.421','2026-09-24 18:55:29.277','2026-09-24 19:07:16.422'),(2,'user1','user1@workflowx.local','$2a$10$SiyhSohOsoc29a9gAzxxD.CAhCxZP5p.z8rsNHixxoF1oSt9PcL1i','测试用户一','ACTIVE',NULL,'2026-09-24 18:55:29.279','2026-09-24 18:55:29.279');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'workflowx'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24 19:18:07
