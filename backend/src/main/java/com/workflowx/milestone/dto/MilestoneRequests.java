package com.workflowx.milestone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 里程碑请求集（V19）。 */
public final class MilestoneRequests {

    private MilestoneRequests() {
    }

    public record CreateMilestoneRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 100, message = "name 最长 100 字符")
            String name,

            @Size(max = 500, message = "description 最长 500 字符")
            String description,

            LocalDateTime dueDate) {
    }

    public record UpdateMilestoneRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 100, message = "name 最长 100 字符")
            String name,

            @Size(max = 500, message = "description 最长 500 字符")
            String description,

            LocalDateTime dueDate,

            String status) {
    }
}
