package com.workflowx.org.vo;

import com.workflowx.org.entity.Organization;

import java.time.LocalDateTime;

/** 组织视图对象（P4-01）。 */
public record OrganizationVO(
        Long id,
        String name,
        String code,
        Long ownerId,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static OrganizationVO from(Organization org) {
        return new OrganizationVO(org.getId(), org.getName(), org.getCode(),
                org.getOwnerId(), org.getDescription(), org.getCreatedAt(), org.getUpdatedAt());
    }
}
