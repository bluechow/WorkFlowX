-- ============================================================
-- WorkFlowX V3: RBAC 系统权限种子（产品数据, dev/prod 均执行; ADR-012）
-- 编码规范: {resource}:{action}（docs/architecture/rbac.md §2）
-- 幂等: 全部 NOT EXISTS 守卫，重复执行不产生重复数据
-- 绑定: ADMIN → 全部 14 项; MEMBER → 暂无管理权限（业务权限随 Phase 4+ 扩展）
-- ============================================================

-- ===== 系统权限（14 项, 全部 API 类型） =====
INSERT INTO permissions (code, name, type, description)
SELECT 'user:list', '用户列表', 'API', '分页查询用户'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'user:get', '用户详情', 'API', '按 ID 查询用户'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'user:create', '创建用户', 'API', '管理员创建用户'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'user:update', '更新用户', 'API', '管理员更新用户基本信息'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'user:status', '用户状态', 'API', '启用/禁用用户（禁用即踢线）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:status');
INSERT INTO permissions (code, name, type, description)
SELECT 'user:assign_role', '分配用户角色', 'API', '给用户分配/回收角色'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'user:assign_role');

INSERT INTO permissions (code, name, type, description)
SELECT 'role:list', '角色列表', 'API', '查询全部角色'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'role:get', '角色详情', 'API', '查询角色及其权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'role:create', '创建角色', 'API', '创建新角色'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'role:update', '更新角色', 'API', '更新角色名称/描述'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'role:delete', '删除角色', 'API', '删除非系统角色'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:delete');
INSERT INTO permissions (code, name, type, description)
SELECT 'role:assign_permission', '分配角色权限', 'API', '给角色分配/回收权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'role:assign_permission');

INSERT INTO permissions (code, name, type, description)
SELECT 'permission:list', '权限列表', 'API', '查询全部权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'permission:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'permission:get', '权限详情', 'API', '按编码查询权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'permission:get');

-- ===== ADMIN 角色绑定全部系统权限 =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'user:list', 'user:get', 'user:create', 'user:update', 'user:status', 'user:assign_role',
    'role:list', 'role:get', 'role:create', 'role:update', 'role:delete', 'role:assign_permission',
    'permission:list', 'permission:get'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
