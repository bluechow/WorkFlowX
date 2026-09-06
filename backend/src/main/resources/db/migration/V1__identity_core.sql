-- ============================================================
-- WorkFlowX V1: 身份域核心表（RBAC 五层结构, ADR-003）
-- 约定: utf8mb4 / snake_case / BIGINT 自增主键 / 统一时间字段（§8）
-- ============================================================

CREATE TABLE users (
    id            BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    email         VARCHAR(100) NOT NULL COMMENT '邮箱',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码散列（bcrypt），禁止明文',
    nickname      VARCHAR(50)  NULL COMMENT '显示昵称',
    status        ENUM('ACTIVE','DISABLED','LOCKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
    last_login_at DATETIME(3)  NULL COMMENT '最后登录时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

CREATE TABLE roles (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(50)  NOT NULL COMMENT '角色编码（如 ADMIN/MANAGER/MEMBER）',
    name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200) NULL COMMENT '描述',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE permissions (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(100) NOT NULL COMMENT '权限编码（resource:action，如 issue:delete）',
    name        VARCHAR(50)  NOT NULL COMMENT '权限名称',
    type        ENUM('MENU','API','BUTTON') NOT NULL DEFAULT 'API' COMMENT '权限类型',
    description VARCHAR(200) NULL COMMENT '描述',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限';

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联';

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 ID',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_perm (permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联';
