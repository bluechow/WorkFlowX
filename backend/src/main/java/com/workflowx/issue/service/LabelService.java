package com.workflowx.issue.service;

import com.workflowx.issue.dto.LabelRequests;
import com.workflowx.issue.vo.LabelVO;

import java.util.List;

/**
 * 工作项标签服务（V17，Final Edition FP-1）。
 * 权限：读=issue:list，管理=issue:update（Controller 层）；项目归属 Service 校验。
 * 删除标签 → 绑定经 FK 级联解除（不删除工作项）。
 */
public interface LabelService {

    List<LabelVO> list(Long projectId);

    LabelVO create(Long projectId, LabelRequests.CreateLabelRequest request, Long operatorId);

    LabelVO update(Long projectId, Long labelId, LabelRequests.UpdateLabelRequest request);

    void delete(Long projectId, Long labelId);
}
