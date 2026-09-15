-- ============================================================
-- WorkFlowX V10: Issue 模块系统权限种子（Phase 6; P6-03; ADR-016）
-- issue:transition 属 Phase 7，本阶段不引入（ADR-016.3）
-- ============================================================

INSERT INTO permissions (code, name, type, description)
SELECT 'issue:list', 'Issue 列表', 'API', '项目内分页查询 Issue'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'issue:get', 'Issue 详情', 'API', '查询 Issue 详情'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'issue:create', '创建 Issue', 'API', '创建 Issue（数据级叠加：须为项目成员）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'issue:update', '更新 Issue', 'API', '更新 Issue/状态（数据级叠加：须为项目成员）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'issue:assign', '分派 Issue', 'API', '变更 Issue 经办人（数据级叠加：须为项目成员）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'issue:assign');

-- ADMIN 绑定全部 Issue 权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'issue:list', 'issue:get', 'issue:create', 'issue:update', 'issue:assign'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
