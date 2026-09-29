package com.workflowx.milestone.vo;

import com.workflowx.milestone.entity.Milestone;

import java.time.LocalDateTime;

/** 里程碑视图对象（V19）：含进度统计（归属工作项的完结占比）。 */
public record MilestoneVO(
        Long id,
        Long projectId,
        String name,
        String description,
        LocalDateTime dueDate,
        String status,
        LocalDateTime createdAt,
        int totalIssues,
        int doneIssues) {

    public static MilestoneVO of(Milestone m, int totalIssues, int doneIssues) {
        return new MilestoneVO(m.getId(), m.getProjectId(), m.getName(), m.getDescription(),
                m.getDueDate(), m.getStatus() == null ? null : m.getStatus().name(),
                m.getCreatedAt(), totalIssues, doneIssues);
    }
}
