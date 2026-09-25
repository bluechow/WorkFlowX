-- ============================================================
-- WorkFlowX V15: 测试用例库（Phase 20 / V1.1; P20-02; ADR-022）
-- 目录树（自引用）+ 用例（项目内编号复用 ADR-016 行锁方案）
-- 目录删除: 子目录级联删除；目录内用例 directory_id 置 NULL（退回未分类，不丢数据）
-- 权限: testcase:list/get/create/update/delete（5 项，49→54，仅 ADMIN 绑定）
-- ============================================================

-- 用例编号计数器（与 issue_seq 同方案，避免 SELECT MAX+1）
ALTER TABLE projects
    ADD COLUMN testcase_seq BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '测试用例序号计数器（已分配的最大 testcase_no）' AFTER issue_seq;

CREATE TABLE test_case_directories (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id  BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    parent_id   BIGINT UNSIGNED NULL COMMENT '父目录（NULL=根目录）',
    name        VARCHAR(100) NOT NULL COMMENT '目录名',
    created_by  BIGINT UNSIGNED NOT NULL COMMENT '创建人（逻辑引用 users.id）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tcdir_project (project_id),
    KEY idx_tcdir_parent (parent_id),
    CONSTRAINT fk_tcdir_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tcdir_parent FOREIGN KEY (parent_id) REFERENCES test_case_directories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试用例目录（树）';

CREATE TABLE test_cases (
    id             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id     BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    directory_id   BIGINT UNSIGNED NULL COMMENT '所属目录（NULL=未分类）',
    testcase_no    BIGINT UNSIGNED NOT NULL COMMENT '项目内序号（业务编号=project.key-TC-testcase_no）',
    title          VARCHAR(200) NOT NULL COMMENT '用例标题',
    preconditions  TEXT NULL COMMENT '前置条件',
    steps          TEXT NULL COMMENT '测试步骤（文本行）',
    expected       TEXT NULL COMMENT '预期结果',
    case_type      ENUM('FUNCTIONAL','REGRESSION','SMOKE','SECURITY','PERFORMANCE') NOT NULL DEFAULT 'FUNCTIONAL' COMMENT '用例类型',
    priority       ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    status         ENUM('DRAFT','ACTIVE','DEPRECATED') NOT NULL DEFAULT 'DRAFT' COMMENT '用例状态',
    created_by     BIGINT UNSIGNED NOT NULL COMMENT '创建人（逻辑引用 users.id）',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_cases_project_no (project_id, testcase_no),
    KEY idx_tc_project_dir (project_id, directory_id),
    KEY idx_tc_project_status (project_id, status),
    KEY idx_tc_creator (created_by),
    CONSTRAINT fk_tc_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tc_directory FOREIGN KEY (directory_id) REFERENCES test_case_directories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试用例';

-- 权限种子（幂等，风格对齐 V11/V12/V14）
INSERT INTO permissions (code, name, type, description)
SELECT 'testcase:list', '用例列表', 'API', '查看项目测试用例与目录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testcase:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'testcase:get', '用例详情', 'API', '查看测试用例详情'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testcase:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'testcase:create', '创建用例', 'API', '创建测试用例与用例目录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testcase:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'testcase:update', '编辑用例', 'API', '编辑测试用例与用例目录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testcase:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'testcase:delete', '删除用例', 'API', '删除测试用例与用例目录'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'testcase:delete');

-- ADMIN 绑定（5 项逐一幂等绑定）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'testcase:list', 'testcase:get', 'testcase:create', 'testcase:update', 'testcase:delete'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
