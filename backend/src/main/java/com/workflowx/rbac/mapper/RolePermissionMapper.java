package com.workflowx.rbac.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限绑定数据访问（P3-02）：复合主键关联表，注解 SQL（P2-08 先例）。
 */
@Mapper
public interface RolePermissionMapper {

    @Select("""
            SELECT p.code FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.id
            """)
    List<String> findPermissionCodesByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT p.code FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.id
            JOIN user_roles ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY p.id
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM role_permissions
            WHERE role_id = #{roleId} AND permission_id = #{permissionId}
            """)
    int existsBinding(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Insert("""
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT #{roleId}, p.id FROM permissions p WHERE p.code = #{permissionCode}
            """)
    int insertByPermissionCode(@Param("roleId") Long roleId, @Param("permissionCode") String permissionCode);

    @Delete("""
            DELETE FROM role_permissions
            WHERE role_id = #{roleId}
              AND permission_id IN (SELECT id FROM permissions WHERE code = #{permissionCode})
            """)
    int deleteByPermissionCode(@Param("roleId") Long roleId, @Param("permissionCode") String permissionCode);
}
