package com.workflowx.issue.service.impl;

import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.issue.service.WorkflowService;
import com.workflowx.issue.vo.IssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Workflow 领域服务实现（P7-02，ADR-017）。
 * 状态机收敛于本类 VALID_TRANSITIONS，禁止散落 Controller；
 * 并发保护: IssueMapper.transition 条件 UPDATE（WHERE status = fromStatus），不满足 → 409。
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    /** 正式状态转换矩阵（ADR-017，唯一合法集合） */
    private static final Map<IssueStatus, Set<IssueStatus>> VALID_TRANSITIONS = Map.of(
            IssueStatus.OPEN, Set.of(IssueStatus.IN_PROGRESS),
            IssueStatus.IN_PROGRESS, Set.of(IssueStatus.RESOLVED),
            IssueStatus.RESOLVED, Set.of(IssueStatus.TESTING),
            IssueStatus.TESTING, Set.of(IssueStatus.CLOSED, IssueStatus.REOPENED),
            IssueStatus.REOPENED, Set.of(IssueStatus.IN_PROGRESS)
    );

    private final IssueMapper issueMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final com.workflowx.project.mapper.ProjectMapper projectMapper;
    private final com.workflowx.notification.service.NotificationService notificationService;

    @Override
    public Set<IssueStatus> allowedTargets(IssueStatus from) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of());
    }

    @Override
    @Transactional
    public IssueVO transition(Long projectId, Long issueId, IssueStatus fromStatus,
                              IssueStatus toStatus, Long operatorId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || !issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("issue", issueId);
        }
        // 数据级: 操作者须为项目成员（ADR-016.4）
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可执行状态流转");
        }
        // 合法矩阵校验
        Set<IssueStatus> targets = allowedTargets(issue.getStatus());
        if (!targets.contains(toStatus)) {
            throw new BusinessException(409, "非法状态流转: " + issue.getStatus() + " → " + toStatus
                    + "（允许: " + targets + "）");
        }
        // 并发保护: 条件 UPDATE——当前状态已被并发变更时 affected=0 → 409
        int updated = issueMapper.transitionStatus(issueId, fromStatus.name(), toStatus.name());
        if (updated == 0) {
            Issue current = issueMapper.selectById(issueId);
            throw new BusinessException(409, "状态流转冲突: 当前状态为 "
                    + (current == null ? "UNKNOWN" : current.getStatus()) + "，请刷新后重试");
        }
        // P9-07: 流转成功 → 通知 assignee + reporter（排除操作者本人，Set 去重；共事务）
        java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (issue.getAssigneeId() != null) {
            recipients.add(issue.getAssigneeId());
        }
        recipients.add(issue.getReporterId());
        recipients.remove(operatorId);
        if (!recipients.isEmpty()) {
            String projectKey = projectMapper.selectById(projectId).getKey();
            for (Long recipientId : recipients) {
                notificationService.notifyIssueStatusChanged(projectKey, issue.getIssueNo(),
                        issue.getTitle(), issueId, toStatus.name(), recipientId, operatorId);
            }
        }
        return IssueVO.from(issueMapper.selectById(issueId));
    }
}
