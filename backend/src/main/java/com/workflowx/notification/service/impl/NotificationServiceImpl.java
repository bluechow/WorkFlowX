package com.workflowx.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.notification.entity.Notification;
import com.workflowx.notification.entity.NotificationType;
import com.workflowx.notification.mapper.NotificationMapper;
import com.workflowx.notification.service.NotificationService;
import com.workflowx.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知领域实现（P9-04; ADR-019）。
 * ownership 收敛在 SQL/查询条件（recipient_id 等值），Service 再次校验确保数据隔离不只依赖 Controller。
 * 去重: 触发点收敛于各业务 Service 单一方法（P9-08），收件人集合在触发方去重；本类不做幂等表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 关联对象类型常量（当前仅 Issue；跳转语义由前端处理） */
    public static final String RELATED_ISSUE = "ISSUE";

    private final NotificationMapper notificationMapper;

    @Override
    public void create(NotificationType type, Long recipientId, String title, String content,
                       String relatedType, Long relatedId) {
        if (type == null || recipientId == null) {
            throw new IllegalStateException("通知创建参数缺失: type/recipientId 不可为空（编程错误）");
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
    }

    @Override
    public PageVO<NotificationVO> listMy(Long recipientId, Boolean isRead, long page, long size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, recipientId);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        wrapper.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);
        IPage<Notification> result = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        return PageVO.of(result.convert(NotificationVO::from));
    }

    @Override
    public long unreadCount(Long recipientId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, recipientId)
                .eq(Notification::getIsRead, false));
    }

    @Override
    public void markRead(Long recipientId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getRecipientId().equals(recipientId)) {
            // 不存在或非本人通知统一 404，不泄露他人通知存在性（对齐项目跨资源语义）
            throw new ResourceNotFoundException("notification", notificationId);
        }
        // 幂等: 已读（is_read=1）时 affected=0 亦视为成功
        notificationMapper.markRead(notificationId, recipientId, LocalDateTime.now());
    }

    @Override
    public int markAllRead(Long recipientId) {
        return notificationMapper.markAllRead(recipientId, LocalDateTime.now());
    }

    @Override
    public void notifyIssueAssigned(String projectKey, Long issueNo, String issueTitle, Long issueId,
                                    Long recipientId, Long operatorId) {
        create(NotificationType.ISSUE_ASSIGNED, recipientId, "Issue 已分派给您",
                String.format("%s-%d %s（由 #%d 分派）", projectKey, issueNo, issueTitle, operatorId),
                RELATED_ISSUE, issueId);
    }

    @Override
    public void notifyIssueStatusChanged(String projectKey, Long issueNo, String issueTitle, Long issueId,
                                         String toStatus, Long recipientId, Long operatorId) {
        create(NotificationType.ISSUE_STATUS_CHANGED, recipientId, "Issue 状态变更为 " + toStatus,
                String.format("%s-%d %s（由 #%d 流转）", projectKey, issueNo, issueTitle, operatorId),
                RELATED_ISSUE, issueId);
    }

    @Override
    public void notifyIssueCommented(String projectKey, Long issueNo, String issueTitle, Long issueId,
                                     Long recipientId, Long operatorId) {
        create(NotificationType.ISSUE_COMMENTED, recipientId, "Issue 有新评论",
                String.format("%s-%d %s（#%d 发表评论）", projectKey, issueNo, issueTitle, operatorId),
                RELATED_ISSUE, issueId);
    }
}
