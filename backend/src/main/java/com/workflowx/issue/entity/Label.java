package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作项标签（V17；项目内命名空间，名称唯一由 uk 约束）。 */
@Data
@TableName("labels")
public class Label {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    /** 展示颜色（6 位 HEX，Service 层校验） */
    private String color;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
