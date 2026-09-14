package com.workflowx.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 部门实体，映射 V4 表 departments（org 内树：parent_id NULL=根，ADR-013）。 */
@Data
@TableName("departments")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;

    private Long parentId;

    private String name;

    private String code;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
