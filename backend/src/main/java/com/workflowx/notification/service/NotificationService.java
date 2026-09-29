package com.workflowx.notification.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.notification.entity.NotificationType;
import com.workflowx.notification.vo.NotificationVO;

/**
 * 站内通知领域能力（P9-04; ADR-019）。
 * 数据范围: currentUser == recipient（self 资源，无 notification:* 权限码，对齐 /auth/me 先例）。
 * 事务: create 由业务触发点在主业务 @Transactional 内调用（共事务，主业务回滚通知同回滚）。
 * 通知失败禁止吞异常: insert 失败上抛 → 主业务事务回滚（明确行为，ADR-019）。
 */
public interface NotificationService {

    /**
     * 业务触发创建（内部接口）。recipient/title/content 由触发方以真实业务数据组装，
     * recipient 必须是已验证的业务相关用户（assignee/reporter 均已在业务链路验证存在）。
     */
    void create(NotificationType type, Long recipientId, String title, String content,
                String relatedType, Long relatedId);

    /** 我的通知分页（稳定排序 created_at DESC, id DESC）；isRead null=全部。 */
    PageVO<NotificationVO> listMy(Long recipientId, Boolean isRead, long page, long size);

    /** 类型筛选（FP-7「@我的」；type 可空=全部） */
    PageVO<NotificationVO> listMy(Long recipientId, Boolean isRead, String type, long page, long size);

    /** 评论 @提及通知（FP-7）：逐一发送（排除提及者本人） */
    void notifyIssueMentioned(String projectKey, Long issueNo, String issueTitle, Long issueId,
                              java.util.List<Long> mentionedUserIds, Long operatorId);

    /** 未读数量（与数据库实际状态一致）。 */
    long unreadCount(Long recipientId);

    /** 标记单条已读（幂等；不属于本人 → 404，不泄露存在性）。 */
    void markRead(Long recipientId, Long notificationId);

    /** 全部已读：数据库条件更新，只影响当前用户未读通知；返回影响条数。 */
    int markAllRead(Long recipientId);

    // ===== 业务触发便捷方法（文案组装收敛于通知模块；recipient 由触发方去重并排除操作者）=====

    /** Issue 分派通知（创建即分派/变更分派）。 */
    void notifyIssueAssigned(String projectKey, Long issueNo, String issueTitle, Long issueId,
                             Long recipientId, Long operatorId);

    /** Issue 状态流转通知。 */
    void notifyIssueStatusChanged(String projectKey, Long issueNo, String issueTitle, Long issueId,
                                  String toStatus, Long recipientId, Long operatorId);

    /** Issue 新评论通知。 */
    void notifyIssueCommented(String projectKey, Long issueNo, String issueTitle, Long issueId,
                              Long recipientId, Long operatorId);
}
