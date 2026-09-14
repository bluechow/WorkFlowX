package com.workflowx.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织成员实体，映射 V4 表 organization_members。
 * 复合主键 (orgId, userId)——MyBatis-Plus 不支持复合 @TableId，
 * 本实体仅用于结果映射（selectById 不可用）；写操作走 OrganizationMemberMapper 注解 SQL。
 */
@Data
@TableName("organization_members")
public class OrganizationMember {

    private Long orgId;

    private Long userId;

    private OrgMemberType role;

    private Long departmentId;

    private LocalDateTime createdAt;
}
