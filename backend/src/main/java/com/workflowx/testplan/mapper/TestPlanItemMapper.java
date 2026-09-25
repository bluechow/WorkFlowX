package com.workflowx.testplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.testplan.entity.TestPlanItem;
import org.apache.ibatis.annotations.Mapper;

/** 测试计划条目数据访问（Phase 21/V1.1）：CRUD 由 BaseMapper 提供。 */
@Mapper
public interface TestPlanItemMapper extends BaseMapper<TestPlanItem> {
}
