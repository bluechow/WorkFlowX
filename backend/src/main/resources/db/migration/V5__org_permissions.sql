-- ============================================================
-- WorkFlowX V5: 组织模块系统权限种子（产品数据, ADR-013 / 编码规范 rbac.md §2）
-- 幂等: NOT EXISTS 守卫；ADMIN 绑定全部
-- ============================================================

INSERT INTO permissions (code, name, type, description)
SELECT 'org:list', '组织列表', 'API', '分页查询组织'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'org:get', '组织详情', 'API', '查询组织详情与成员'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'org:create', '创建组织', 'API', '创建新组织（创建者成为 OWNER）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'org:update', '更新组织', 'API', '更新组织基本信息'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'org:delete', '删除组织', 'API', '删除组织（数据级叠加：仅组织 OWNER）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:delete');
INSERT INTO permissions (code, name, type, description)
SELECT 'org:assign_member', '组织成员管理', 'API', '添加/移除组织成员'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'org:assign_member');

INSERT INTO permissions (code, name, type, description)
SELECT 'department:list', '部门列表', 'API', '查询组织内部门'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'department:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'department:get', '部门详情', 'API', '查询部门详情'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'department:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'department:create', '创建部门', 'API', '创建部门'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'department:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'department:update', '更新部门', 'API', '更新部门（含调整父级，防环）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'department:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'department:delete', '删除部门', 'API', '删除部门（子级提升为根）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'department:delete');

-- ADMIN 绑定全部组织模块权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'org:list', 'org:get', 'org:create', 'org:update', 'org:delete', 'org:assign_member',
    'department:list', 'department:get', 'department:create', 'department:update', 'department:delete'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
