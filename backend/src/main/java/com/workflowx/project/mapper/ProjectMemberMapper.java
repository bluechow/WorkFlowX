package com.workflowx.project.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 项目成员数据访问（P5-04）：复合主键关联表，注解 SQL（P2-08 先例）。 */
@Mapper
public interface ProjectMemberMapper {

    @Select("""
            SELECT project_id, user_id, role, created_at
            FROM project_members
            WHERE project_id = #{projectId}
            ORDER BY created_at, user_id
            """)
    List<com.workflowx.project.entity.ProjectMember> findMembersByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT project_id, user_id, role, created_at
            FROM project_members
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    com.workflowx.project.entity.ProjectMember findMember(@Param("projectId") Long projectId,
                                                          @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM project_members
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int existsBinding(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO project_members (project_id, user_id, role)
            VALUES (#{projectId}, #{userId}, #{role})
            """)
    int insert(@Param("projectId") Long projectId, @Param("userId") Long userId, @Param("role") String role);

    @Delete("""
            DELETE FROM project_members WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int deleteMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
