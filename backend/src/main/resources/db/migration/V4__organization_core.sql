-- ============================================================
-- WorkFlowX V4: 组织架构核心表（organizations / organization_members / departments）
-- 设计: docs/architecture/organization.md（P4-01, ADR-013）
-- 约定: utf8mb4 / snake_case / BIGINT 自增主键 / 统一时间字段（§8）
-- ============================================================

CREATE TABLE organizations (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '组织名称',
    code        VARCHAR(50)  NOT NULL COMMENT '组织编码',
    owner_id    BIGINT UNSIGNED NOT NULL COMMENT '所有者用户 ID（逻辑引用 users.id，不建外键）',
    description VARCHAR(500) NULL COMMENT '描述',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_code (code),
    KEY idx_organizations_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织';

CREATE TABLE departments (
    id         BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    org_id     BIGINT UNSIGNED NOT NULL COMMENT '所属组织',
    parent_id  BIGINT UNSIGNED NULL COMMENT '父部门 ID（NULL=根部门，自引用）',
    name       VARCHAR(100) NOT NULL COMMENT '部门名称',
    code       VARCHAR(50)  NOT NULL COMMENT '部门编码（组织内唯一）',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_org_code (org_id, code),
    KEY idx_departments_org (org_id),
    KEY idx_departments_parent (parent_id),
    CONSTRAINT fk_departments_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门（组织内两级以上树）';

CREATE TABLE organization_members (
    org_id        BIGINT UNSIGNED NOT NULL COMMENT '组织 ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '成员用户 ID',
    role          ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '组织内角色',
    department_id BIGINT UNSIGNED NULL COMMENT '所属部门（可空=未分配）',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    PRIMARY KEY (org_id, user_id),
    KEY idx_org_members_user (user_id),
    KEY idx_org_members_dept (department_id),
    CONSTRAINT fk_org_members_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_dept FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织成员（用户-组织归属+部门归属）';
