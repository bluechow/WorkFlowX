package com.workflowx.milestone.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.milestone.dto.MilestoneRequests;
import com.workflowx.milestone.entity.Milestone;
import com.workflowx.milestone.mapper.MilestoneMapper;
import com.workflowx.milestone.service.MilestoneService;
import com.workflowx.milestone.vo.MilestoneVO;
import com.workflowx.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 里程碑实现（V19）。
 * 进度统计：按 milestone_id 聚合归属工作项（DONE=RESOLVED/CLOSED）。
 * 名称项目内唯一：先查后写 + uk 兜底（409）。
 */
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneMapper milestoneMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;

    @Override
    public List<MilestoneVO> list(Long projectId) {
        requireProject(projectId);
        List<Milestone> milestones = milestoneMapper.selectList(
                new LambdaQueryWrapper<Milestone>()
                        .eq(Milestone::getProjectId, projectId)
                        .orderByAsc(Milestone::getDueDate)
                        .orderByAsc(Milestone::getId));
        if (milestones.isEmpty()) {
            return List.of();
        }
        // 进度聚合（一次分组查询）
        Map<Long, int[]> stats = issueMapper.selectList(
                        new LambdaQueryWrapper<Issue>()
                                .eq(Issue::getProjectId, projectId)
                                .isNotNull(Issue::getMilestoneId)
                                .select(Issue::getMilestoneId, Issue::getStatus))
                .stream()
                .collect(Collectors.groupingBy(Issue::getMilestoneId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            int done = (int) list.stream()
                                    .filter(i -> i.getStatus() == IssueStatus.RESOLVED
                                            || i.getStatus() == IssueStatus.CLOSED).count();
                            return new int[]{list.size(), done};
                        })));
        return milestones.stream()
                .map(m -> MilestoneVO.of(m,
                        stats.getOrDefault(m.getId(), new int[]{0, 0})[0],
                        stats.getOrDefault(m.getId(), new int[]{0, 0})[1]))
                .toList();
    }

    @Override
    public MilestoneVO create(Long projectId, MilestoneRequests.CreateMilestoneRequest request, Long operatorId) {
        requireProject(projectId);
        Milestone m = new Milestone();
        m.setProjectId(projectId);
        m.setName(request.name().trim());
        m.setDescription(request.description());
        m.setDueDate(request.dueDate());
        m.setStatus(Milestone.Status.OPEN);
        m.setCreatedBy(operatorId);
        try {
            milestoneMapper.insert(m);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "里程碑名已存在");
        }
        return MilestoneVO.of(m, 0, 0);
    }

    @Override
    public MilestoneVO update(Long projectId, Long milestoneId, MilestoneRequests.UpdateMilestoneRequest request) {
        Milestone m = requireMilestoneInProject(projectId, milestoneId);
        m.setName(request.name().trim());
        m.setDescription(request.description());
        m.setDueDate(request.dueDate());
        if ("DONE".equals(request.status()) || "OPEN".equals(request.status())) {
            m.setStatus(Milestone.Status.valueOf(request.status()));
        } else if (request.status() != null) {
            throw new BusinessException(400, "status 仅允许 OPEN/DONE");
        }
        try {
            milestoneMapper.updateById(m);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "里程碑名已存在");
        }
        return list(projectId).stream()
                .filter(vo -> vo.id().equals(milestoneId)).findFirst().orElse(MilestoneVO.of(m, 0, 0));
    }

    @Override
    public void delete(Long projectId, Long milestoneId) {
        requireMilestoneInProject(projectId, milestoneId);
        // FK SET NULL：归属工作项退回未归属
        milestoneMapper.deleteById(milestoneId);
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
    }

    private Milestone requireMilestoneInProject(Long projectId, Long milestoneId) {
        Milestone m = milestoneMapper.selectById(milestoneId);
        if (m == null || !m.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("milestone", milestoneId);
        }
        return m;
    }
}
