-- ============================================================
-- WorkFlowX V7: 项目模块系统权限种子（产品数据, ADR-014 / 编码规范 rbac.md §2）
-- project:delete 为预留 authority（当前无物理删除端点, 归档代替）
-- ============================================================

INSERT INTO permissions (code, name, type, description)
SELECT 'project:list', '项目列表', 'API', '分页查询项目'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'project:get', '项目详情', 'API', '查询项目详情'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'project:create', '创建项目', 'API', '创建项目（数据级叠加：须为目标组织成员）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'project:update', '更新项目', 'API', '更新项目/归档恢复（数据级叠加：须为组织成员）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'project:delete', '删除项目', 'API', '预留（当前归档代替物理删除）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'project:delete');

-- ADMIN 绑定全部项目权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'project:list', 'project:get', 'project:create', 'project:update', 'project:delete'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
