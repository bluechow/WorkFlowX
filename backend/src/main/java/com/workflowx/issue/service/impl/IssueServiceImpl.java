package com.workflowx.issue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.IssuePageQuery;
import com.workflowx.issue.dto.UpdateIssueRequest;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.IssueVO;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Issue 领域服务实现（P6-02）。
 * 异常约定: issue/project 缺失 404；业务规则 400/422；数据级 403（ForbiddenException）。
 * 并发序号: 同事务内 UPDATE projects 行锁递增 → SELECT 读取 → 写入 issue_no；
 *          UNIQUE(project_id, issue_no) 兜底（ADR-016）。
 */
@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final com.workflowx.notification.service.NotificationService notificationService;
    private final com.workflowx.audit.service.AuditService auditService;

    @Override
    @Transactional
    public IssueVO create(Long projectId, CreateIssueRequest request, Long operatorId) {
        Project project = requireProject(projectId);
        requireProjectMembership(projectId, operatorId);

        if (request.severity() != null && !IssueType.BUG.equals(request.type())) {
            throw new BusinessException(400, "severity 仅适用于 BUG 类型");
        }
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(projectId, request.assigneeId());
        }

        // ADR-016: 同事务行锁递增序号后读取，并发安全
        if (projectMapper.incrementIssueSeq(projectId) == 0) {
            throw new ResourceNotFoundException("project", projectId);
        }
        Long issueNo = projectMapper.selectIssueSeq(projectId);

        Issue issue = new Issue();
        issue.setProjectId(projectId);
        issue.setIssueNo(issueNo);
        issue.setTitle(request.title());
        issue.setDescription(request.description());
        issue.setType(request.type());
        issue.setPriority(request.priority() == null
                ? com.workflowx.issue.entity.IssuePriority.MEDIUM : request.priority());
        issue.setSeverity(request.severity());
        issue.setStatus(com.workflowx.issue.entity.IssueStatus.OPEN);
        issue.setReporterId(operatorId);
        issue.setAssigneeId(request.assigneeId());
        issueMapper.insert(issue);
        auditService.record("ISSUE", "CREATE", "issue:" + issue.getId(),
                "创建 Issue " + project.getKey() + "-" + issue.getIssueNo() + " " + issue.getTitle(),
                true, operatorId);
        // P9-07: 创建即分派 → 通知 assignee（排除操作者本人；共事务，主业务回滚通知同回滚）
        if (issue.getAssigneeId() != null && !issue.getAssigneeId().equals(operatorId)) {
            notificationService.notifyIssueAssigned(project.getKey(), issue.getIssueNo(),
                    issue.getTitle(), issue.getId(), issue.getAssigneeId(), operatorId);
        }
        return IssueVO.from(requireIssue(projectId, issue.getId()));
    }

    @Override
    public IssueVO getById(Long projectId, Long issueId) {
        return IssueVO.from(requireIssue(projectId, issueId));
    }

    @Override
    public PageVO<IssueVO> page(Long projectId, IssuePageQuery query) {
        requireProject(projectId);
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId);
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(w -> w.like(Issue::getTitle, query.keyword())
                    .or().like(Issue::getDescription, query.keyword()));
        }
        if (query.issueNo() != null) {
            wrapper.eq(Issue::getIssueNo, query.issueNo());
        }
        if (query.type() != null) {
            wrapper.eq(Issue::getType, query.type());
        }
        if (query.priority() != null) {
            wrapper.eq(Issue::getPriority, query.priority());
        }
        if (query.severity() != null) {
            wrapper.eq(Issue::getSeverity, query.severity());
        }
        if (query.status() != null) {
            wrapper.eq(Issue::getStatus, query.status());
        }
        if (query.reporterId() != null) {
            wrapper.eq(Issue::getReporterId, query.reporterId());
        }
        if (query.assigneeId() != null) {
            wrapper.eq(Issue::getAssigneeId, query.assigneeId());
        }
        wrapper.orderByDesc(Issue::getCreatedAt).orderByDesc(Issue::getId);
        Page<Issue> result = issueMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        return PageVO.of(result.convert(IssueVO::from));
    }
    @Override
    public java.util.List<IssueVO> listForBoard(Long projectId) {
        requireProject(projectId);
        // 看板为全量视图：上限 500 条防御超大项目；组内排序交由前端（状态分列后按活动时间）
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .orderByDesc(Issue::getUpdatedAt)
                .last("LIMIT 500");
        return issueMapper.selectList(wrapper).stream().map(IssueVO::from).toList();
    }

    @Override
    @Transactional
    public IssueVO update(Long projectId, Long issueId, UpdateIssueRequest request, Long operatorId) {
        Issue issue = requireIssue(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        Long previousAssigneeId = issue.getAssigneeId();

        if (request.severity() != null && !IssueType.BUG.equals(issue.getType())) {
            throw new BusinessException(400, "severity 仅适用于 BUG 类型");
        }
        // ADR-016 哨兵: assigneeId=0 表示取消分派（清空），跳过成员校验
        boolean clearAssignee = request.assigneeId() != null && request.assigneeId() == 0;
        if (request.assigneeId() != null && !clearAssignee) {
            requireAssigneeIsProjectMember(projectId, request.assigneeId());
        }
        if (StringUtils.hasText(request.title())) {
            issue.setTitle(request.title());
        }
        if (request.description() != null) {
            issue.setDescription(request.description());
        }
        if (request.priority() != null) {
            issue.setPriority(request.priority());
        }
        if (request.severity() != null) {
            issue.setSeverity(request.severity());
        }
        // assigneeId 语义: null=未提供（不变）；0=哨兵表示清空分派（置 NULL）
        if (request.assigneeId() != null) {
            issue.setAssigneeId(clearAssignee ? null : request.assigneeId());
        }
        if (clearAssignee) {
            // MP updateById 默认忽略 null 字段——显式 set null 才能写库
            issueMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Issue>()
                            .eq(Issue::getId, issueId)
                            .set(Issue::getAssigneeId, null));
        } else {
            issueMapper.updateById(issue);
        }
        // P9-07: 变更分派 → 通知新 assignee（取消分派不通知；与旧值相同不重复通知）
        Long newAssigneeId = issue.getAssigneeId();
        if (newAssigneeId != null && !newAssigneeId.equals(previousAssigneeId)
                && !newAssigneeId.equals(operatorId)) {
            Project project = projectMapper.selectById(issue.getProjectId());
            notificationService.notifyIssueAssigned(project.getKey(), issue.getIssueNo(),
                    issue.getTitle(), issueId, newAssigneeId, operatorId);
        }
        return IssueVO.from(requireIssue(projectId, issueId));
    }


    private Issue requireIssue(Long projectId, Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        // 防跨项目访问: issue 必须归属于路径中的项目
        if (issue == null || !issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("issue", issueId);
        }
        return issue;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        return project;
    }

    /** 数据级权限（ADR-016）: 操作者必须是项目成员 */
    private void requireProjectMembership(Long projectId, Long operatorId) {
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可操作该项目的 Issue");
        }
    }

    /** assignee 约束: 必须是项目成员（组织成员不够） */
    private void requireAssigneeIsProjectMember(Long projectId, Long assigneeId) {
        if (projectMemberMapper.findMember(projectId, assigneeId) == null) {
            throw new BusinessException(400, "assignee 必须是项目成员");
        }
    }
}
