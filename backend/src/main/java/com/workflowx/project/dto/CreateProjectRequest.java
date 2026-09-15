package com.workflowx.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求（P5-01）。key: 大写字母/数字 2-20，字母开头（Issue 编号前缀，全局唯一）。
 */
public record CreateProjectRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 100, message = "name 最长 100 字符")
        String name,

        @NotBlank(message = "key 不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,19}$", message = "key 只能包含大写字母、数字，且以字母开头，长度 2-20")
        String key,

        @jakarta.validation.constraints.NotNull(message = "orgId 不能为空")
        Long orgId,

        @Size(max = 500, message = "description 最长 500 字符")
        String description) {
}
