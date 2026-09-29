package com.workflowx.notification.entity;

/**
 * 通知类型（P9-03; ADR-019）。最小集：仅覆盖既有业务的真实触发点，不预设未来类型。
 * 触发点: Issue 创建/变更分派（ISSUE_ASSIGNED）、状态流转（ISSUE_STATUS_CHANGED）、评论（ISSUE_COMMENTED）。
 */
public enum NotificationType {

    /** Issue 被分派给用户（创建即分派或变更分派） */
    ISSUE_ASSIGNED,

    /** 用户关注的 Issue 状态发生流转 */
    ISSUE_STATUS_CHANGED,

    /** 用户关注的 Issue 有新评论 */
    ISSUE_COMMENTED,

    /** 评论中 @提及（FP-7） */
    ISSUE_MENTIONED
}
