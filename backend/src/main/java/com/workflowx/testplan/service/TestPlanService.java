package com.workflowx.testplan.service;

import com.workflowx.common.web.PageVO;
import com.workflowx.testplan.dto.AddTestPlanItemsRequest;
import com.workflowx.testplan.dto.CreateTestPlanRequest;
import com.workflowx.testplan.dto.ExecuteItemRequest;
import com.workflowx.testplan.dto.UpdateTestPlanRequest;
import com.workflowx.testplan.vo.TestPlanItemVO;
import com.workflowx.testplan.vo.TestPlanVO;

import java.util.List;

/**
 * 测试计划领域能力（V1.1; ADR-023）。
 * 权限: testplan:* authority（Controller）+ 项目成员（本层，读写均要求；成员校验先于目标查找）。
 */
public interface TestPlanService {

    TestPlanVO create(Long projectId, CreateTestPlanRequest request, Long operatorId);

    /** 项目内计划分页（含每计划实时统计）。 */
    PageVO<TestPlanVO> page(Long projectId, long page, long size, Long operatorId);

    /** 计划详情（含全部条目 + 关联用例摘要，按用例编号升序）。 */
    TestPlanVO get(Long projectId, Long planId, Long operatorId);

    /** 计划内条目列表。 */
    List<TestPlanItemVO> items(Long projectId, Long planId, Long operatorId);

    /** 更新名称/状态。 */
    TestPlanVO update(Long projectId, Long planId, UpdateTestPlanRequest request, Long operatorId);

    void delete(Long projectId, Long planId, Long operatorId);

    /** 向计划添加用例（须属本项目且未在计划内；COMPLETED 计划拒绝）。返回实际新增条数。 */
    int addItems(Long projectId, Long planId, AddTestPlanItemsRequest request, Long operatorId);

    /** 从计划移除条目（COMPLETED 计划拒绝）。 */
    void removeItem(Long projectId, Long planId, Long itemId, Long operatorId);

    /** 执行打结果（幂等覆盖；FAIL/BLOCKED 可关联 Issue；首次执行自动 NOT_STARTED→RUNNING）。 */
    TestPlanItemVO execute(Long projectId, Long planId, Long itemId, ExecuteItemRequest request, Long operatorId);
}
