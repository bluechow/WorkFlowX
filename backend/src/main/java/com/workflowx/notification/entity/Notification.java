package com.workflowx.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体，映射 V13 表 notifications（data-dictionary §13 + ADR-019 扩展）。
 * recipient 服务端决定（业务触发点传入），客户端不可伪造；
 * read_at 与 is_read 同步写（已读条件更新一并置位）。
 */
@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recipientId;

    private NotificationType type;

    private String title;

    private String content;

    /** 关联对象类型（当前仅 ISSUE），与 relatedId 一起供前端跳转 */
    private String relatedType;

    private Long relatedId;

    private Boolean isRead;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
