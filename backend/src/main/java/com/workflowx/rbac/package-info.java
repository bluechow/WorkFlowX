/**
 * rbac 模块：角色-权限-用户绑定领域（P3-01/P3-02）。
 * 职责: Role/Permission 领域 CRUD、角色-权限绑定、用户-角色绑定、用户权限实时查询。
 * 边界: 本阶段仅领域能力（无 REST Controller，属后续任务）；登录链路的角色查询
 *       （auth 模块 AuthRoleQueryMapper）保持不动（ADR-012）。
 */
package com.workflowx.rbac;
