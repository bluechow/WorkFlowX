-- ============================================================
-- WorkFlowX V11: Workflow 权限种子（Phase 7; P7-03; ADR-017）
-- PATCH status 端点自本迁移起使用 issue:transition（自 issue:update 迁移）
-- ============================================================

INSERT INTO permissions (code, name, type, description)
SELECT 'issue:transition', 'Issue 状态流转', 'API', '按正式矩阵执行 Issue 状态流转（非法流转 409）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:transition');

-- ADMIN 绑定
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code = 'issue:transition'
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
