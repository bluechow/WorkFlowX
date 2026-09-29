-- ============================================================
-- WorkFlowX V18: 项目活动流（Final Edition FP-2; Blueprint §4）
-- 记录真实业务事件（创建/流转/分派/评论/附件/标签/关联/截止变更），
-- 由各 Service 在业务事务内写入；项目删除级联清理。
-- 权限：读=issue:list（与项目工作项读一致）；无独立权限码（59 项冻结）。
-- ============================================================

CREATE TABLE activities (
    id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id   BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    issue_id     BIGINT UNSIGNED NULL COMMENT '关联工作项（项目级事件为 NULL，逻辑引用不建外键——issue 删除后活动保留历史）',
    actor_id     BIGINT UNSIGNED NOT NULL COMMENT '操作人（逻辑引用 users.id）',
    action       VARCHAR(30) NOT NULL COMMENT '动作（CREATE/UPDATE/TRANSITION/ASSIGN/COMMENT/ATTACHMENT/LABEL/LINK/DUE）',
    target       VARCHAR(100) NULL COMMENT '业务对象（issue:{id}）',
    summary      VARCHAR(500) NOT NULL COMMENT '中文摘要（展示用，白名单字段组装）',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_activities_project_time (project_id, created_at DESC),
    KEY idx_activities_issue (issue_id),
    CONSTRAINT fk_activities_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目活动流';
