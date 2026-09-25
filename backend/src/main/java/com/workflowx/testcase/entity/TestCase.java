package com.workflowx.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例实体，映射 V15 表 test_cases（ADR-022）。
 * created_by 服务端绑定；testcase_no 由服务端行锁分配（ADR-016 同方案）。
 */
@Data
@TableName("test_cases")
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 所属目录；NULL=未分类（目录删除时置 NULL，用例不丢） */
    private Long directoryId;

    private Long testcaseNo;

    private String title;

    private String preconditions;

    private String steps;

    private String expected;

    private TestCaseType caseType;

    private TestCasePriority priority;

    private TestCaseStatus status;

    /** 创建者，服务端绑定，不接受客户端传入 */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
