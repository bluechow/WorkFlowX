package com.workflowx.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 添加组织成员请求（P4-01）。role 可空默认 MEMBER。 */
public record AddOrganizationMemberRequest(
        @jakarta.validation.constraints.NotNull(message = "userId 不能为空")
        Long userId,

        @Pattern(regexp = "^(ADMIN|MEMBER)$", message = "role 仅允许 ADMIN/MEMBER（OWNER 由创建组织时自动产生）")
        String role,

        Long departmentId) {
}
