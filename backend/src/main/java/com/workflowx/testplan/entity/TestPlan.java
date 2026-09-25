package com.workflowx.testplan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 测试计划实体，映射 V16 表 test_plans（V1.1; ADR-023）。 */
@Data
@TableName("test_plans")
public class TestPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    /** NOT_STARTED → RUNNING → COMPLETED */
    private TestPlanStatus status;

    /** 创建者，服务端绑定 */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum TestPlanStatus { NOT_STARTED, RUNNING, COMPLETED }
}
