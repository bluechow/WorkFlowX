package com.workflowx.notification.vo;

import com.workflowx.notification.entity.Notification;
import com.workflowx.notification.entity.NotificationType;

import java.time.LocalDateTime;

/** 通知视图对象（P9-04）。relatedType+relatedId 供前端跳转（当前仅 ISSUE）。 */
public record NotificationVO(
        Long id,
        NotificationType type,
        String title,
        String content,
        String relatedType,
        Long relatedId,
        Boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        /** related Issue 所属项目（跳转 /system/projects/{projectId}/issues 用）；Issue 已删时为 null */
        Long projectId) {

    public static NotificationVO from(Notification notification) {
        return from(notification, null);
    }

    public static NotificationVO from(Notification notification, Long projectId) {
        return new NotificationVO(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getContent(),
                notification.getRelatedType(), notification.getRelatedId(),
                notification.getIsRead(), notification.getReadAt(), notification.getCreatedAt(),
                projectId);
    }
}
