package com.workflowx.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 标签请求集（V17）。 */
public final class LabelRequests {

    private LabelRequests() {
    }

    public record CreateLabelRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 50, message = "name 最长 50 字符")
            String name,

            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color 须为 6 位 HEX（如 #409EFF）")
            String color) {
    }

    public record UpdateLabelRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 50, message = "name 最长 50 字符")
            String name,

            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color 须为 6 位 HEX（如 #409EFF）")
            String color) {
    }
}
