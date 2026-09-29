package com.workflowx.milestone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 里程碑（V19，轻量：名称唯一 + 目标日期 + 开启/完成）。 */
@Data
@TableName("milestones")
public class Milestone {

    public enum Status { OPEN, DONE }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private String description;

    private LocalDateTime dueDate;

    private Status status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
