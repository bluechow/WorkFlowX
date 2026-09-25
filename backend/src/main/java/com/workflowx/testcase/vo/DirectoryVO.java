package com.workflowx.testcase.vo;

import com.workflowx.testcase.entity.TestCaseDirectory;

import java.time.LocalDateTime;

/** 用例目录视图对象（Phase 20）。树结构由前端按 parentId 组装。 */
public record DirectoryVO(
        Long id,
        Long projectId,
        Long parentId,
        String name,
        Long createdBy,
        LocalDateTime createdAt) {

    public static DirectoryVO from(TestCaseDirectory directory) {
        return new DirectoryVO(directory.getId(), directory.getProjectId(), directory.getParentId(),
                directory.getName(), directory.getCreatedBy(), directory.getCreatedAt());
    }
}
