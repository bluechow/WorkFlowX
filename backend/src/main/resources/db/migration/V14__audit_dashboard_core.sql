-- ============================================================
-- WorkFlowX V14: Audit 审计日志 + Dashboard 权限（Phase 10; P10-02; ADR-020）
-- 数据字典 §15 audit_logs 为权威命名；最小扩展 trace_id/summary/user_agent/created_at
-- （项目已有 TraceIdFilter 链路；summary 为业务摘要，严禁密码/令牌等敏感数据入库）。
-- issue_status_transitions（字典 §14）不建表：规则在 WorkflowService 内存矩阵、
-- 操作事实由本表记录、用户告知由 notifications 承担——ADR-020 决策 D。
-- ============================================================

CREATE TABLE audit_logs (
    id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT UNSIGNED NULL COMMENT '操作人（匿名请求为 NULL，逻辑引用 users.id）',
    module       VARCHAR(30) NOT NULL COMMENT '业务模块（AUTH/USER/ORG/PROJECT/ISSUE/COMMENT/ATTACHMENT）',
    action       VARCHAR(30) NOT NULL COMMENT '操作（LOGIN/LOGIN_FAIL/LOGOUT/CREATE/UPDATE/DELETE/STATUS/TRANSITION/ASSIGN_MEMBER/UPLOAD）',
    http_method  VARCHAR(10) NOT NULL COMMENT 'HTTP Method',
    uri          VARCHAR(200) NOT NULL COMMENT '请求 URI',
    ip           VARCHAR(45) NOT NULL COMMENT '客户端 IP（兼容 IPv6）',
    target       VARCHAR(100) NULL COMMENT '目标对象（如 issue:123）',
    summary      VARCHAR(500) NULL COMMENT '业务摘要（白名单字段组装，不含敏感数据）',
    success      TINYINT(1) NOT NULL COMMENT '操作结果',
    trace_id     VARCHAR(32) NULL COMMENT '链路 ID（与日志/响应头一致）',
    user_agent   VARCHAR(255) NULL COMMENT 'User-Agent',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_audit_user_time (user_id, created_at),
    KEY idx_audit_module (module),
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志（高价值操作事实）';

-- audit 权限种子（幂等，风格对齐 V11/V12）
INSERT INTO permissions (code, name, type, description)
SELECT 'audit:list', '审计日志列表', 'API', '查询系统审计日志（高敏感，仅管理角色）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'audit:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'audit:get', '审计日志详情', 'API', '查看单条审计日志详情'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'audit:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'dashboard:view', '仪表盘查看', 'API', '查看数据统计仪表盘（数据范围按角色/成员关系收敛）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'dashboard:view');

-- ADMIN 绑定（3 项逐一幂等绑定）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN ('audit:list', 'audit:get', 'dashboard:view')
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
