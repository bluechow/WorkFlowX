-- ============================================================
-- WorkFlowX V8: 项目成员表 + 成员管理权限（Phase 5; P5-04; ADR-015）
-- 规则: 用户须先为项目所属组织成员，方可成为项目成员（Service 层校验）
-- 角色: OWNER(创建者,唯一,不可移除/降级) / MANAGER / MEMBER（ADR-015 最小集）
-- ============================================================

CREATE TABLE project_members (
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '项目 ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '成员用户 ID',
    role        ENUM('OWNER','MANAGER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '项目内角色',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    PRIMARY KEY (project_id, user_id),
    KEY idx_project_members_user (user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员';

-- 成员管理权限（{resource}:{action} 规范）
INSERT INTO permissions (code, name, type, description)
SELECT 'project:assign_member', '项目成员管理', 'API', '添加/移除项目成员'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:assign_member');

-- ADMIN 绑定
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code = 'project:assign_member'
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
