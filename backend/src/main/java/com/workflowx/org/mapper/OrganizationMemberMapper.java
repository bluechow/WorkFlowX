package com.workflowx.org.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 组织成员数据访问（P4-01）：复合主键关联表，注解 SQL（P2-08 先例）。
 */
@Mapper
public interface OrganizationMemberMapper {

    @Select("""
            SELECT org_id, user_id, role, department_id, created_at
            FROM organization_members
            WHERE org_id = #{orgId}
            ORDER BY created_at, user_id
            """)
    List<com.workflowx.org.entity.OrganizationMember> findMembersByOrgId(@Param("orgId") Long orgId);

    @Select("""
            SELECT org_id, user_id, role, department_id, created_at
            FROM organization_members
            WHERE org_id = #{orgId} AND user_id = #{userId}
            """)
    com.workflowx.org.entity.OrganizationMember findMember(@Param("orgId") Long orgId, @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM organization_members WHERE user_id = #{userId}
            """)
    int countByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT role FROM organization_members WHERE org_id = #{orgId} AND user_id = #{userId}
            """)
    String findMemberRole(@Param("orgId") Long orgId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO organization_members (org_id, user_id, role)
            VALUES (#{orgId}, #{userId}, #{role})
            """)
    int insert(@Param("orgId") Long orgId, @Param("userId") Long userId, @Param("role") String role);

    @Update("""
            UPDATE organization_members SET department_id = #{departmentId}
            WHERE org_id = #{orgId} AND user_id = #{userId}
            """)
    int updateDepartment(@Param("orgId") Long orgId, @Param("userId") Long userId,
                         @Param("departmentId") Long departmentId);

    @Delete("""
            DELETE FROM organization_members WHERE org_id = #{orgId} AND user_id = #{userId}
            """)
    int deleteMember(@Param("orgId") Long orgId, @Param("userId") Long userId);
}
