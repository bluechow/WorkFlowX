-- ============================================================
-- WorkFlowX V16: 测试计划与执行（V1.1; ADR-023）
-- 计划从用例库挑选用例组成执行集；逐条打结果；FAIL 关联 Issue。
-- ============================================================

CREATE TABLE test_plans (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    name        VARCHAR(200) NOT NULL COMMENT '计划名称',
    status      ENUM('NOT_STARTED','RUNNING','COMPLETED') NOT NULL DEFAULT 'NOT_STARTED' COMMENT '计划状态',
    created_by  BIGINT UNSIGNED NOT NULL COMMENT '创建人（逻辑引用 users.id）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tp_project (project_id),
    CONSTRAINT fk_tp_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试计划';

CREATE TABLE test_plan_items (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    plan_id     BIGINT UNSIGNED NOT NULL COMMENT '所属计划',
    case_id     BIGINT UNSIGNED NOT NULL COMMENT '关联用例',
    result      ENUM('PENDING','PASS','FAIL','BLOCKED') NOT NULL DEFAULT 'PENDING' COMMENT '执行结果',
    executed_by BIGINT UNSIGNED NULL COMMENT '执行人（未执行为 NULL）',
    executed_at DATETIME(3)  NULL COMMENT '执行时间',
    note        VARCHAR(500) NULL COMMENT '执行备注',
    issue_id    BIGINT UNSIGNED NULL COMMENT '关联 Bug（FAIL/BLOCKED 时）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tp_items (plan_id, case_id),
    KEY idx_tpi_plan (plan_id),
    KEY idx_tpi_case (case_id),
    CONSTRAINT fk_tpi_plan FOREIGN KEY (plan_id) REFERENCES test_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_tpi_case FOREIGN KEY (case_id) REFERENCES test_cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试计划条目';

-- 权限种子（幂等）：54→59
INSERT INTO permissions (code, name, type, description)
SELECT 'testplan:list', '测试计划列表', 'API', '查看项目测试计划'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testplan:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'testplan:get', '测试计划详情', 'API', '查看测试计划详情与执行记录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testplan:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'testplan:create', '创建测试计划', 'API', '创建测试计划并挑选用例'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testplan:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'testplan:update', '编辑测试计划', 'API', '编辑计划/执行打结果/关联 Bug'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testplan:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'testplan:delete', '删除测试计划', 'API', '删除测试计划'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testplan:delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'testplan:list', 'testplan:get', 'testplan:create', 'testplan:update', 'testplan:delete'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
