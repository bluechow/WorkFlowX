package com.workflowx.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 项目数据访问（P5-01）：CRUD 由 BaseMapper 提供。 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * Issue 序号原子递增（P6-02，ADR-016）。
     * 同事务 UPDATE 行锁串行化并发分配；调用方须在事务内随后读取 issue_seq。
     * @return 影响行数（项目不存在为 0）
     */
    @Update("UPDATE projects SET issue_seq = issue_seq + 1 WHERE id = #{id}")
    int incrementIssueSeq(@Param("id") Long id);

    @Select("SELECT issue_seq FROM projects WHERE id = #{id}")
    Long selectIssueSeq(@Param("id") Long id);
}
