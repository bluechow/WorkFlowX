package com.workflowx.project.dto;

import jakarta.validation.constraints.Size;

/** 更新项目请求（P5-01）：仅 name/description；key/orgId 不可改；状态走独立端点。 */
public record UpdateProjectRequest(
        @Size(max = 100, message = "name 最长 100 字符")
        String name,

        @Size(max = 500, message = "description 最长 500 字符")
        String description) {
}
