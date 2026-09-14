package com.workflowx.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限实体，映射 V1 表 permissions。
 * code 规范 {resource}:{action}（docs/architecture/rbac.md §2）；系统权限由 PermissionService 常量守护禁止删除。
 */
@Data
@TableName("permissions")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private PermissionType type;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
