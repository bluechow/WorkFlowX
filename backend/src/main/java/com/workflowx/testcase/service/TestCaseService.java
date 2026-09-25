package com.workflowx.testcase.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.dto.TestCasePageQuery;
import com.workflowx.testcase.dto.UpdateTestCaseRequest;
import com.workflowx.testcase.vo.TestCaseVO;

/**
 * 测试用例领域能力（Phase 20; ADR-022）。
 * 权限: authority（Controller）+ 项目成员（本层，读写均要求）。
 * 成员校验先于目标查找；编号 = 项目内行锁递增（ADR-016 同方案）。
 */
public interface TestCaseService {

    TestCaseVO create(Long projectId, CreateTestCaseRequest request, Long operatorId);

    PageVO<TestCaseVO> page(Long projectId, TestCasePageQuery query, Long operatorId);

    TestCaseVO getById(Long projectId, Long testcaseId, Long operatorId);

    TestCaseVO update(Long projectId, Long testcaseId, UpdateTestCaseRequest request, Long operatorId);

    void delete(Long projectId, Long testcaseId, Long operatorId);
}
