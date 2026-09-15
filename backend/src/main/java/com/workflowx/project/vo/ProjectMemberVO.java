package com.workflowx.project.vo;

import com.workflowx.project.entity.ProjectMember;
import com.workflowx.project.entity.ProjectMemberRole;

import java.time.LocalDateTime;

/** 项目成员视图对象（P5-04）。 */
public record ProjectMemberVO(
        Long projectId,
        Long userId,
        ProjectMemberRole role,
        LocalDateTime createdAt) {

    public static ProjectMemberVO from(ProjectMember member) {
        return new ProjectMemberVO(member.getProjectId(), member.getUserId(),
                member.getRole(), member.getCreatedAt());
    }
}
