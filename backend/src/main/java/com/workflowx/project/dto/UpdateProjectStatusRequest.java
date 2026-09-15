package com.workflowx.project.dto;

import com.workflowx.project.entity.ProjectStatus;
import jakarta.validation.constraints.NotNull;

/** 项目状态变更请求（P5-02）：归档/恢复。 */
public record UpdateProjectStatusRequest(
        @NotNull(message = "status 不能为空")
        ProjectStatus status) {
}
