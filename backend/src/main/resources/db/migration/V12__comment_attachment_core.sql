-- ============================================================
-- WorkFlowX V12: Comment 与 Attachment 数据模型（Phase 8; P8-02/P8-06; ADR-018）
-- 数据字典 §11 issue_comments / §12 attachments；文件二进制存 MinIO，库内仅元数据
-- ============================================================

CREATE TABLE issue_comments (
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    issue_id    BIGINT UNSIGNED NOT NULL COMMENT '所属 Issue',
    author_id   BIGINT UNSIGNED NOT NULL COMMENT '作者（创建者，逻辑引用 users.id）',
    content     TEXT NOT NULL COMMENT '内容',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_comments_issue (issue_id, created_at),
    KEY idx_comments_author (author_id),
    CONSTRAINT fk_comments_issue FOREIGN KEY (issue_id) REFERENCES issues (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue 评论';

CREATE TABLE attachments (
    id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    issue_id     BIGINT UNSIGNED NOT NULL COMMENT '所属 Issue',
    object_key   VARCHAR(200) NOT NULL COMMENT 'MinIO 对象键（服务端生成）',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名（仅元数据，不参与对象键）',
    file_size    BIGINT UNSIGNED NOT NULL COMMENT '字节数',
    content_type VARCHAR(100) NOT NULL COMMENT '客户端声明的 MIME 类型（不作为放行依据）',
    uploader_id  BIGINT UNSIGNED NOT NULL COMMENT '上传者（逻辑引用 users.id）',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attachments_object_key (object_key),
    KEY idx_attachments_issue (issue_id, created_at),
    KEY idx_attachments_uploader (uploader_id),
    CONSTRAINT fk_attachments_issue FOREIGN KEY (issue_id) REFERENCES issues (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Issue 附件（元数据）';

-- comment 权限种子（幂等，风格对齐 V11）
INSERT INTO permissions (code, name, type, description)
SELECT 'comment:list', '评论列表', 'API', '查看项目 Issue 的评论列表'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'comment:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'comment:get', '评论详情', 'API', '查看单条评论'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'comment:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'comment:create', '创建评论', 'API', '在项目 Issue 下发表评论'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'comment:create');
INSERT INTO permissions (code, name, type, description)
SELECT 'comment:update', '编辑评论', 'API', '编辑本人发表的评论'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'comment:update');
INSERT INTO permissions (code, name, type, description)
SELECT 'comment:delete', '删除评论', 'API', '删除本人发表的评论'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'comment:delete');

-- attachment 权限种子（幂等）
INSERT INTO permissions (code, name, type, description)
SELECT 'attachment:list', '附件列表', 'API', '查看项目 Issue 的附件列表'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'attachment:list');
INSERT INTO permissions (code, name, type, description)
SELECT 'attachment:get', '附件下载', 'API', '下载项目 Issue 的附件（后端鉴权流式读取）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'attachment:get');
INSERT INTO permissions (code, name, type, description)
SELECT 'attachment:upload', '上传附件', 'API', '向项目 Issue 上传附件（白名单类型与大小限制）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'attachment:upload');
INSERT INTO permissions (code, name, type, description)
SELECT 'attachment:delete', '删除附件', 'API', '删除本人上传的附件（MinIO 对象与元数据一并删除）'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'attachment:delete');

-- ADMIN 绑定（9 项逐一幂等绑定）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r JOIN permissions p ON p.code IN (
    'comment:list', 'comment:get', 'comment:create', 'comment:update', 'comment:delete',
    'attachment:list', 'attachment:get', 'attachment:upload', 'attachment:delete'
)
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
