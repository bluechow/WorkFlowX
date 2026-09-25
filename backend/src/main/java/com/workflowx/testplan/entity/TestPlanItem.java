package com.workflowx.testplan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 测试计划条目实体，映射 V16 表 test_plan_items（V1.1; ADR-023）。 */
@Data
@TableName("test_plan_items")
public class TestPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** 关联用例（随用例删除级联） */
    private Long caseId;

    /** PENDING → PASS / FAIL / BLOCKED */
    private ItemResult result;

    /** 执行人（未执行为 NULL） */
    private Long executedBy;

    private LocalDateTime executedAt;

    private String note;

    /** 关联 Bug（FAIL/BLOCKED 时） */
    private Long issueId;

    private LocalDateTime createdAt;

    public enum ItemResult { PENDING, PASS, FAIL, BLOCKED }
}
