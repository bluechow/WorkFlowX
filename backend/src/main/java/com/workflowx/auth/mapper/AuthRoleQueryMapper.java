package com.workflowx.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 登录所需的角色编码查询（P2-08）。
 * 自定义 SQL 原因: 跨 users↔user_roles↔roles 三表关联，超出 BaseMapper 单表能力；
 * RBAC 完整 Mapper 体系属 Phase 3，此处仅提供登录签发 roles claim 所需的最小只读查询。
 */
@Mapper
public interface AuthRoleQueryMapper {

    @Select("""
            SELECT r.code
            FROM roles r
            JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
