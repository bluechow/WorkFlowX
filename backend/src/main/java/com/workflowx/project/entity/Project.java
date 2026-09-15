package com.workflowx.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目实体，映射 V6 表 projects。
 * `key` 为 MySQL 保留字风格列名——MP 默认驼峰转下划线会得到 key，与列名一致，无需显式列名；
 * 为清晰起见显式标注。status 归档策略见 ProjectStatus。
 */
@Data
@TableName("projects")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;

    @TableField("`key`")
    private String key;

    private String name;

    private String description;

    private ProjectStatus status;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
