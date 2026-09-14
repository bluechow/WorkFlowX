package com.workflowx.org.vo;

import com.workflowx.org.entity.OrganizationMember;
import com.workflowx.org.entity.OrgMemberType;

import java.time.LocalDateTime;

/** 组织成员视图对象（P4-01）。 */
public record OrganizationMemberVO(
        Long orgId,
        Long userId,
        OrgMemberType role,
        Long departmentId,
        LocalDateTime createdAt) {

    public static OrganizationMemberVO from(OrganizationMember member) {
        return new OrganizationMemberVO(member.getOrgId(), member.getUserId(),
                member.getRole(), member.getDepartmentId(), member.getCreatedAt());
    }
}
