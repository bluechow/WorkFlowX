package com.workflowx.org;

import java.util.Set;

/**
 * 组织模块常量（ADR-013）。
 * 组织成员角色为 DB ENUM（OWNER/ADMIN/MEMBER），以 OrgMemberType 承载；
 * 系统权限清单见 RbacConstants（org/department 权限于 P4-02 V5 种子纳入）。
 */
public final class OrgConstants {

    /** 组织拥有者角色（DB ENUM 值）；每个组织恰好一个，不可移除/降级 */
    public static final String MEMBER_ROLE_OWNER = "OWNER";

    private OrgConstants() {
    }
}
