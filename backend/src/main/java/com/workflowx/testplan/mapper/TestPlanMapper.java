package com.workflowx.testplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.testplan.entity.TestPlan;
import org.apache.ibatis.annotations.Mapper;

/** 测试计划数据访问（Phase 21/V1.1）：CRUD 由 BaseMapper 提供。 */
@Mapper
public interface TestPlanMapper extends BaseMapper<TestPlan> {
}
