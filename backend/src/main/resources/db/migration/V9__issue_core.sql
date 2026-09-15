-- ============================================================
-- WorkFlowX V9: Issue 核心表（Phase 6; P6-01; 字段遵循 data-dictionary §10）
-- issue_no: 项目内递增序号，UNIQUE(project_id, issue_no)；
--           并发分配经 projects.issue_seq 行锁递增（ADR-016）
-- ============================================================

-- 项目级 Issue 序号计数器（并发分配：同事务 UPDATE 行锁递增后读取）
ALTER TABLE projects
    ADD COLUMN issue_seq BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Issue 序号计数器（已分配的最大 issue_no）' AFTER owner_id;

CREATE TABLE issues (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    issue_no    BIGINT UNSIGNED NOT NULL COMMENT '项目内序号（业务编号=project.key-issue_no）',
    title       VARCHAR(200) NOT NULL COMMENT '标题',
    description TEXT NULL COMMENT '描述',
    type        ENUM('BUG','TASK','FEATURE','IMPROVEMENT') NOT NULL DEFAULT 'TASK' COMMENT '类型',
    priority    ENUM('LOW','MEDIUM','HIGH','URGENT') NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    severity    ENUM('S1','S2','S3','S4') NULL COMMENT 'Bug 严重程度（仅 type=BUG，可空）',
    status      ENUM('OPEN','IN_PROGRESS','RESOLVED','TESTING','CLOSED','REOPENED') NOT NULL DEFAULT 'OPEN' COMMENT '状态（流转规则属 Phase 7）',
    reporter_id BIGINT UNSIGNED NOT NULL COMMENT '报告人（创建者，逻辑引用 users.id）',
    assignee_id BIGINT UNSIGNED NULL COMMENT '经办人（须为项目成员，逻辑引用 users.id）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issues_project_no (project_id, issue_no),
    KEY idx_issues_project_status (project_id, status),
    KEY idx_issues_project_type (project_id, type),
    KEY idx_issues_project_priority (project_id, priority),
    KEY idx_issues_assignee (assignee_id),
    KEY idx_issues_reporter (reporter_id),
    CONSTRAINT fk_issues_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue';
