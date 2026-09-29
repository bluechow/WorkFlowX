package com.workflowx.milestone.service;

import com.workflowx.milestone.dto.MilestoneRequests;
import com.workflowx.milestone.vo.MilestoneVO;

import java.util.List;

/** 里程碑服务（V19，Final Edition FP-4）：管理=project:update，读=issue:list。 */
public interface MilestoneService {

    /** 项目里程碑列表（按目标日期升序；含每个里程碑的工作项进度统计） */
    List<MilestoneVO> list(Long projectId);

    MilestoneVO create(Long projectId, MilestoneRequests.CreateMilestoneRequest request, Long operatorId);

    MilestoneVO update(Long projectId, Long milestoneId, MilestoneRequests.UpdateMilestoneRequest request);

    /** 删除里程碑：归属工作项退回未归属（FK SET NULL），不删除工作项 */
    void delete(Long projectId, Long milestoneId);
}
