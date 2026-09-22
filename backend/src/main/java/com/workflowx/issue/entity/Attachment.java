package com.workflowx.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Issue 附件元数据实体，映射 V12 表 attachments（data-dictionary §12）。
 * 文件二进制存 MinIO（Master Prompt §13）；objectKey 由服务端生成，库内 UNIQUE 兜底。
 */
@Data
@TableName("attachments")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private String objectKey;

    /** 原始文件名，仅作展示元数据，不参与对象键与路径 */
    private String fileName;

    private Long fileSize;

    /** 客户端声明的 MIME 类型，不作为安全放行依据 */
    private String contentType;

    private Long uploaderId;

    private LocalDateTime createdAt;
}
