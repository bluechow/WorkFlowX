-- ============================================================
-- WorkFlowX V6: 项目核心表（projects, Phase 5; P5-01）
-- 设计: data-dictionary §8 projects + owner_id 扩展（ADR-014）
-- 归档策略: status ENUM(ACTIVE/ARCHIVED)，无物理删除
-- ============================================================

CREATE TABLE projects (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    org_id      BIGINT UNSIGNED NOT NULL COMMENT '所属组织',
    `key`       VARCHAR(20)  NOT NULL COMMENT '项目标识（全局唯一，Issue 编号前缀，如 WFX）',
    name        VARCHAR(100) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) NULL COMMENT '描述',
    status      ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（归档策略）',
    owner_id    BIGINT UNSIGNED NOT NULL COMMENT '创建者/负责人（逻辑引用 users.id）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_key (`key`),
    KEY idx_projects_org_status (org_id, status),
    KEY idx_projects_owner (owner_id),
    CONSTRAINT fk_projects_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目';
