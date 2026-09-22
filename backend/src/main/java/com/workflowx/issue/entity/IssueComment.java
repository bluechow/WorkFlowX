package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Issue 评论实体，映射 V12 表 issue_comments（data-dictionary §11）。
 * author 由服务端绑定当前登录用户，不接受客户端传入。
 */
@Data
@TableName("issue_comments")
public class IssueComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private Long authorId;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
