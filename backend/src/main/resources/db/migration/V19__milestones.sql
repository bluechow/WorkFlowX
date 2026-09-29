-- ============================================================
-- WorkFlowX V19: 轻量项目计划（Final Edition FP-4; Blueprint §4）
-- 里程碑（项目内名称唯一）+ 工作项归属里程碑（可空，SET NULL）。
-- 权限：里程碑管理复用 project:update；读复用 issue:list（59 项冻结）。
-- ============================================================

CREATE TABLE milestones (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    name        VARCHAR(100) NOT NULL COMMENT '里程碑名（如 V1.0 发布）',
    description VARCHAR(500) NULL COMMENT '描述',
    due_date    DATETIME(3) NULL COMMENT '目标日期（可空=未定）',
    status      ENUM('OPEN','DONE') NOT NULL DEFAULT 'OPEN' COMMENT '状态（轻量：开启/完成）',
    created_by  BIGINT UNSIGNED NOT NULL COMMENT '创建人（逻辑引用 users.id）',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_milestones_project_name (project_id, name),
    KEY idx_milestones_project_due (project_id, due_date),
    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='里程碑';

-- 工作项归属里程碑（可空；里程碑删除时退回未归属，不删工作项）
ALTER TABLE issues
    ADD COLUMN milestone_id BIGINT UNSIGNED NULL COMMENT '归属里程碑（可空）' AFTER due_date,
    ADD KEY idx_issues_milestone (milestone_id),
    ADD CONSTRAINT fk_issues_milestone FOREIGN KEY (milestone_id) REFERENCES milestones (id) ON DELETE SET NULL;
