-- ============================================================
-- WorkFlowX V13: Notification 通知（Phase 9; P9-02; ADR-019）
-- 数据字典 §13 为基础；业务跳转需要 related 字段；read_at 与 is_read 一致性；
-- recipient 逻辑引用 users.id（不建外键，对齐 issues.reporter_id 风格）；
-- 无 notification:* 权限码——通知为 self 资源（ADR-019，对齐 /auth/me 先例）。
-- 无 FK: Issue 删除后通知保留历史，跳转失效由前端 404 兜底（P9 需求明示）。
-- ============================================================

CREATE TABLE notifications (
    id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    recipient_id BIGINT UNSIGNED NOT NULL COMMENT '接收人（逻辑引用 users.id）',
    type         VARCHAR(30) NOT NULL COMMENT '通知类型（ISSUE_ASSIGNED/ISSUE_STATUS_CHANGED/ISSUE_COMMENTED）',
    title        VARCHAR(200) NOT NULL COMMENT '标题（真实业务上下文）',
    content      VARCHAR(1000) NULL COMMENT '内容（业务编号+标题+操作者）',
    related_type VARCHAR(30) NULL COMMENT '关联对象类型（ISSUE）',
    related_id   BIGINT UNSIGNED NULL COMMENT '关联对象 id（issueId，跳转用）',
    is_read      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已读标记',
    read_at      DATETIME(3) NULL COMMENT '已读时间（与 is_read 同步写）',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_notifications_recipient (recipient_id, is_read),
    KEY idx_notifications_recipient_created (recipient_id, created_at),
    KEY idx_notifications_related (related_type, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';
