package com.workflowx.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建部门请求（P4-01）。code 组织内唯一；parentId 可空=根部门。 */
public record CreateDepartmentRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 100, message = "name 最长 100 字符")
        String name,

        @NotBlank(message = "code 不能为空")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$", message = "code 只能包含字母、数字、下划线，且以字母开头，长度 2-50")
        String code,

        @Size(max = 500, message = "description 最长 500 字符")
        String description,

        Long parentId) {
}
