package com.workflowx.project.vo;

import com.workflowx.project.entity.Project;
import com.workflowx.project.entity.ProjectStatus;

import java.time.LocalDateTime;

/** 项目视图对象（P5-01）。 */
public record ProjectVO(
        Long id,
        Long orgId,
        String key,
        String name,
        String description,
        ProjectStatus status,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectVO from(Project project) {
        return new ProjectVO(project.getId(), project.getOrgId(), project.getKey(), project.getName(),
                project.getDescription(), project.getStatus(), project.getOwnerId(),
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
