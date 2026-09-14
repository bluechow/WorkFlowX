package com.workflowx.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 组织实体，映射 V4 表 organizations（ADR-013）。 */
@Data
@TableName("organizations")
public class Organization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Long ownerId;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
