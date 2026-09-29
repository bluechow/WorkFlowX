package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作项关联（V17；source→target 有向，RELATES 相关 / BLOCKS 阻塞）。 */
@Data
@TableName("issue_links")
public class IssueLink {

    public enum LinkType { RELATES, BLOCKS }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceIssueId;

    private Long targetIssueId;

    private LinkType linkType;

    private Long createdBy;

    private LocalDateTime createdAt;
}
