package com.workflowx.org.dto;

import jakarta.validation.constraints.Size;

/** 更新组织请求（P4-01）：name/description 可改；code/owner 不可改。 */
public record UpdateOrganizationRequest(
        @Size(max = 100, message = "name 最长 100 字符")
        String name,

        @Size(max = 500, message = "description 最长 500 字符")
        String description) {
}
