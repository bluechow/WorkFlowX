package com.workflowx.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员实体，映射 V8 表 project_members。
 * 复合主键 (projectId, userId)——仅用于结果映射；写操作走 ProjectMemberMapper 注解 SQL。
 */
@Data
@TableName("project_members")
public class ProjectMember {

    private Long projectId;

    private Long userId;

    private ProjectMemberRole role;

    private LocalDateTime createdAt;
}
