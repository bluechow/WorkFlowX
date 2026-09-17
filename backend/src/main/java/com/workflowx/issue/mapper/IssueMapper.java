package com.workflowx.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.issue.entity.Issue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Issue 数据访问（P6-01）：CRUD 由 BaseMapper 提供；查询条件由 Service 构造。 */
@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 条件状态流转（P7-05，ADR-017）：仅当当前状态等于 fromStatus 时更新。
     * 并发竞争下至多一个请求成功（affected=1），其余 affected=0 → 409。
     */
    @Update("UPDATE issues SET status = #{toStatus}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = #{fromStatus}")
    int transitionStatus(@Param("id") Long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus);
}
