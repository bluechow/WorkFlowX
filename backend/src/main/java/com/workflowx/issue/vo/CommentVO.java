package com.workflowx.issue.vo;

import com.workflowx.issue.entity.IssueComment;

import java.time.LocalDateTime;

/** 评论视图对象（P8-03）。 */
public record CommentVO(
        Long id,
        Long issueId,
        Long authorId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CommentVO from(IssueComment comment) {
        return new CommentVO(comment.getId(), comment.getIssueId(), comment.getAuthorId(),
                comment.getContent(), comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
