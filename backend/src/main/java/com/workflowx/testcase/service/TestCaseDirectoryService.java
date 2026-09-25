package com.workflowx.testcase.service;

import com.workflowx.testcase.dto.CreateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.UpdateTestCaseDirectoryRequest;
import com.workflowx.testcase.vo.DirectoryVO;

import java.util.List;

/**
 * 用例目录领域能力（Phase 20; ADR-022）。
 * 权限: authority（Controller）+ 项目成员（本层，读写均要求——用例库为项目内部资产）。
 * 成员校验先于目标查找（数据级 403 先于目标 404，对齐既有语义）。
 */
public interface TestCaseDirectoryService {

    /** 创建目录（parentId 校验属于本项目且存在；parentId 空=根目录）。 */
    DirectoryVO create(Long projectId, CreateTestCaseDirectoryRequest request, Long operatorId);

    /** 项目内全部目录（平铺，按名称排序；树由前端组装）。 */
    List<DirectoryVO> list(Long projectId, Long operatorId);

    /** 更新（重命名/移动）；移动时防环（新父不能为自身或其后代）。 */
    DirectoryVO update(Long projectId, Long directoryId, UpdateTestCaseDirectoryRequest request, Long operatorId);

    /** 删除目录（子目录级联；目录内用例退回未分类）。 */
    void delete(Long projectId, Long directoryId, Long operatorId);
}
