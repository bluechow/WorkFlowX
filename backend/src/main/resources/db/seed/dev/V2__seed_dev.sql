-- ============================================================
-- WorkFlowX V2: dev 环境种子数据
-- 隔离机制: 本脚本位于 classpath:db/seed/dev，仅 application-dev.yml 配置该
--           location；application-prod.yml 只配置 classpath:db/migration，
--           因此生产环境不会执行本脚本，不存在默认生产凭据。
-- 幂等性: 全部使用 NOT EXISTS 守卫，重复执行不产生重复数据。
-- 密码: 均为 BCrypt(strength 10) 哈希，已经 Spring Security 6.5.2
--        BCryptPasswordEncoder 与 Python bcrypt 双重验证（正确匹配/错误拒绝）。
-- ⚠️ 凭据仅用于开发/测试环境，生产环境禁止使用（见 getting-started.md）。
-- ============================================================

-- 角色（V1 中不存在，补充种子）
INSERT INTO roles (code, name, description)
SELECT 'ADMIN', '系统管理员', '拥有系统全部管理权限'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'ADMIN');

INSERT INTO roles (code, name, description)
SELECT 'MEMBER', '普通成员', '普通团队成员'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'MEMBER');

-- 用户
INSERT INTO users (username, email, password_hash, nickname, status)
SELECT 'admin', 'admin@workflowx.local',
       '$2a$10$hB88hOHWdfiY9DMVNfQMUui7Nyyf8BVg6rEzYCOgdVlo6s.BJjnBW',
       '系统管理员', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, email, password_hash, nickname, status)
SELECT 'user1', 'user1@workflowx.local',
       '$2a$10$SiyhSohOsoc29a9gAzxxD.CAhCxZP5p.z8rsNHixxoF1oSt9PcL1i',
       '测试用户一', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'user1');

-- 用户-角色关联
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u JOIN roles r ON r.code = 'ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u JOIN roles r ON r.code = 'MEMBER'
WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
