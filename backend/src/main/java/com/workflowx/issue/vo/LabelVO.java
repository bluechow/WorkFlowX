package com.workflowx.issue.vo;

import com.workflowx.issue.entity.Label;

import java.time.LocalDateTime;

/** 标签视图对象（V17）。 */
public record LabelVO(
        Long id,
        Long projectId,
        String name,
        String color,
        LocalDateTime createdAt) {

    public static LabelVO from(Label label) {
        return new LabelVO(label.getId(), label.getProjectId(),
                label.getName(), label.getColor(), label.getCreatedAt());
    }
}
