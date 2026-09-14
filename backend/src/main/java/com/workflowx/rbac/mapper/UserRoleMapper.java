package com.workflowx.rbac.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色绑定数据访问（P3-02）。
 * user_roles 为复合主键关联表（V1），MyBatis-Plus 不适合复合键实体，
 * 沿用 AuthRoleQueryMapper 的注解 SQL 先例（P2-08 确立）。
 */
@Mapper
public interface UserRoleMapper {

    @Select("""
            SELECT r.code FROM roles r
            JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM user_roles
            WHERE user_id = #{userId} AND role_id = #{roleId}
            """)
    int existsBinding(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            SELECT #{userId}, r.id FROM roles r WHERE r.code = #{roleCode}
            """)
    int insertByRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Delete("""
            DELETE FROM user_roles
            WHERE user_id = #{userId}
              AND role_id IN (SELECT id FROM roles WHERE code = #{roleCode})
            """)
    int deleteByRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
