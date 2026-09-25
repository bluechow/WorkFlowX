package com.workflowx.testcase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.testcase.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 测试用例数据访问（Phase 20）：CRUD 由 BaseMapper 提供；编号分配用行锁 UPDATE。 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {

    /** 行锁递增项目用例序号（ADR-016 同方案，并发安全）。 */
    @Update("UPDATE projects SET testcase_seq = testcase_seq + 1 WHERE id = #{id}")
    int incrementTestcaseSeq(@Param("id") Long id);

    /** 读取递增后的序号（同事务内；SELECT 须用 @Select，@Update 会破坏返回值映射）。 */
    @org.apache.ibatis.annotations.Select("SELECT testcase_seq FROM projects WHERE id = #{id}")
    Long selectTestcaseSeq(@Param("id") Long id);
}
