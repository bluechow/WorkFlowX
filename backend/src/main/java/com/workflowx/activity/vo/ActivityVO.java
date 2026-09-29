package com.workflowx.activity.vo;

import com.workflowx.activity.entity.Activity;

import java.time.LocalDateTime;

/** 活动视图对象（V18）：中文动作标签由前端映射，此处保留枚举名。 */
public record ActivityVO(
        Long id,
        Long projectId,
        Long issueId,
        Long actorId,
        String action,
        Long issueNo,
        String summary,
        LocalDateTime createdAt) {

    public static ActivityVO of(Activity a, Long issueNo) {
        return new ActivityVO(a.getId(), a.getProjectId(), a.getIssueId(), a.getActorId(),
                a.getAction() == null ? null : a.getAction().name(), issueNo,
                a.getSummary(), a.getCreatedAt());
    }
}
