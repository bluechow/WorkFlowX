package com.workflowx.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评论请求（P8-04）。
 * 安全边界: 不含 authorId（自动取当前用户）/issueId（取自路径）。
 * content 业务上限 10000 字符（ADR-018；UTF-8 下最坏 30000 字节，TEXT 65535 字节内），防异常超长 payload。
 */
public record CreateCommentRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 10000, message = "content 最长 10000 字符")
        String content) {
}
