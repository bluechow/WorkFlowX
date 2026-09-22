package com.workflowx.issue.vo;

import com.workflowx.issue.entity.Attachment;

import java.time.LocalDateTime;

/** 附件元数据视图对象（P8-06）。不含 objectKey 全量暴露以外的下载地址——下载走后端鉴权接口。 */
public record AttachmentVO(
        Long id,
        Long issueId,
        String fileName,
        Long fileSize,
        String contentType,
        Long uploaderId,
        LocalDateTime createdAt) {

    public static AttachmentVO from(Attachment attachment) {
        return new AttachmentVO(attachment.getId(), attachment.getIssueId(), attachment.getFileName(),
                attachment.getFileSize(), attachment.getContentType(), attachment.getUploaderId(),
                attachment.getCreatedAt());
    }
}
