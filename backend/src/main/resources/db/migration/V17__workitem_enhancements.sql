-- ============================================================
-- WorkFlowX V17: 工作项体系增强（Final Edition FP-1; Blueprint §4）
-- 标签（项目内）+ 工作项标签绑定 + 工作项关联（自引用）+ 截止日期
-- 设计: Additive——issues 仅新增可空列 due_date；标签/关联独立成表；
--       权限复用 issue:*（59 项权限数量冻结，无新权限码）
-- ============================================================

-- 工作项标签（项目内命名空间；颜色为前端展示用 6 位 HEX，默认灰）
CREATE TABLE labels (
    id         BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目',
    name       VARCHAR(50)  NOT NULL COMMENT '标签名（项目内唯一）',
    color      VARCHAR(7)   NOT NULL DEFAULT '#909399' COMMENT '展示颜色（HEX）',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人（逻辑引用 users.id）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_labels_project_name (project_id, name),
    KEY idx_labels_project (project_id),
    CONSTRAINT fk_labels_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项标签';

-- 工作项-标签绑定（删除标签级联解除绑定；同一工作项同一标签唯一）
CREATE TABLE issue_labels (
    issue_id BIGINT UNSIGNED NOT NULL COMMENT '工作项',
    label_id BIGINT UNSIGNED NOT NULL COMMENT '标签',
    PRIMARY KEY (issue_id, label_id),
    KEY idx_issue_labels_label (label_id),
    CONSTRAINT fk_issue_labels_issue FOREIGN KEY (issue_id) REFERENCES issues (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_labels_label FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项标签绑定';

-- 工作项关联（自引用有向图；link_type: RELATES 相关 / BLOCKS 阻塞（source 阻塞 target））
-- 约束: 两工作项须同项目（Service 层校验，跨项目 404 语义）；禁止自关联（唯一键含反向序无法表达，Service 校验）
CREATE TABLE issue_links (
    id               BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    source_issue_id  BIGINT UNSIGNED NOT NULL COMMENT '源工作项',
    target_issue_id  BIGINT UNSIGNED NOT NULL COMMENT '目标工作项',
    link_type        ENUM('RELATES','BLOCKS') NOT NULL DEFAULT 'RELATES' COMMENT '关联类型',
    created_by       BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_links (source_issue_id, target_issue_id, link_type),
    KEY idx_issue_links_target (target_issue_id),
    CONSTRAINT fk_issue_links_source FOREIGN KEY (source_issue_id) REFERENCES issues (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_links_target FOREIGN KEY (target_issue_id) REFERENCES issues (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项关联';

-- 截止日期（可空；无时区语义——按本地日期存储，2026-09-29 决策）
ALTER TABLE issues
    ADD COLUMN due_date DATETIME(3) NULL COMMENT '截止日期（可空）' AFTER assignee_id,
    ADD KEY idx_issues_due (due_date);
