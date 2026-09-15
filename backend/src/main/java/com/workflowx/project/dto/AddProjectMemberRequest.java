package com.workflowx.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 添加项目成员请求（P5-04）。role 可空默认 MEMBER；OWNER 由创建项目时自动产生。 */
public record AddProjectMemberRequest(
        @NotNull(message = "userId 不能为空")
        Long userId,

        @Pattern(regexp = "^(MANAGER|MEMBER)$",
                message = "role 仅允许 MANAGER/MEMBER（OWNER 由创建项目时自动产生）")
        String role) {

    public AddProjectMemberRequest {
        if (role == null || role.isBlank()) {
            role = "MEMBER";
        }
    }
}
