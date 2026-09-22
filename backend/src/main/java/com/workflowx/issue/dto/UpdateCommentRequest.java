package com.workflowx.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 编辑评论请求（P8-04）；仅作者本人可编辑（数据级校验在 Service）。 */
public record UpdateCommentRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 10000, message = "content 最长 10000 字符")
        String content) {
}
