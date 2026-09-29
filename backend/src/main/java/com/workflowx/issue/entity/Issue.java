package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Issue 实体，映射 V9 表 issues（data-dictionary §10）。
 * 业务编号 = project.key + "-" + issueNo；状态流转规则属 Phase 7（本阶段 status 仅枚举合法值）。
 */
@Data
@TableName("issues")
public class Issue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long issueNo;

    private String title;

    private String description;

    private IssueType type;

    private IssuePriority priority;

    /** 仅 type=BUG 时可设置，其余类型为 NULL（Service 层校验） */
    private IssueSeverity severity;

    private IssueStatus status;

    /** 创建者自动成为报告人；不接受客户端传入 */
    private Long reporterId;

    /** 可空；非空时必须为项目成员（Service 层校验） */
    private Long assigneeId;

    /** 截止日期（可空；V17 工作项体系增强） */
    private LocalDateTime dueDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
