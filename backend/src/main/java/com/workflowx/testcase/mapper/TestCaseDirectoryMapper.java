package com.workflowx.testcase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflowx.testcase.entity.TestCaseDirectory;
import org.apache.ibatis.annotations.Mapper;

/** 测试用例目录数据访问（Phase 20）：CRUD 由 BaseMapper 提供；树校验由 Service 构造。 */
@Mapper
public interface TestCaseDirectoryMapper extends BaseMapper<TestCaseDirectory> {
}
