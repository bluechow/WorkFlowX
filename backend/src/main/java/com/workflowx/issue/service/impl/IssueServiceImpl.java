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
import com.workflowx.issue.entity.IssueStatus;
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
    private final com.workflowx.issue.mapper.LabelMapper labelMapper;
    private final com.workflowx.issue.mapper.IssueLabelMapper issueLabelMapper;
    private final com.workflowx.issue.mapper.IssueLinkMapper issueLinkMapper;

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
        issue.setDueDate(request.dueDate());
        issueMapper.insert(issue);
        if (request.labelIds() != null && !request.labelIds().isEmpty()) {
            replaceLabels(projectId, issue.getId(), request.labelIds());
        }
        auditService.record("ISSUE", "CREATE", "issue:" + issue.getId(),
                "创建 Issue " + project.getKey() + "-" + issue.getIssueNo() + " " + issue.getTitle(),
                true, operatorId);
        // P9-07: 创建即分派 → 通知 assignee（排除操作者本人；共事务，主业务回滚通知同回滚）
        if (issue.getAssigneeId() != null && !issue.getAssigneeId().equals(operatorId)) {
            notificationService.notifyIssueAssigned(project.getKey(), issue.getIssueNo(),
                    issue.getTitle(), issue.getId(), issue.getAssigneeId(), operatorId);
        }
        return IssueVO.from(requireIssue(projectId, issue.getId()),
                labelsOf(java.util.List.of(issue.getId())).getOrDefault(issue.getId(), java.util.List.of()));
    }

    @Override
    public IssueVO getById(Long projectId, Long issueId) {
        Issue issue = requireIssue(projectId, issueId);
        return IssueVO.from(issue,
                labelsOf(java.util.List.of(issue.getId())).getOrDefault(issue.getId(), java.util.List.of()));
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
        if (query.labelId() != null) {
            // 子查询过滤（labelId 为 Long 类型化参数，无注入面）
            wrapper.inSql(Issue::getId,
                    "SELECT issue_id FROM issue_labels WHERE label_id = " + query.labelId());
        }
        if (query.dueAfter() != null) {
            wrapper.ge(Issue::getDueDate, query.dueAfter().atStartOfDay());
        }
        if (query.dueBefore() != null) {
            wrapper.lt(Issue::getDueDate, query.dueBefore().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(Issue::getCreatedAt).orderByDesc(Issue::getId);
        Page<Issue> result = issueMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        return PageVO.of(result.convert(i -> IssueVO.from(i,
                labelsOf(java.util.List.of(i.getId())).getOrDefault(i.getId(), java.util.List.of()))));
    }
    @Override
    public java.util.List<IssueVO> listForBoard(Long projectId) {
        requireProject(projectId);
        // 看板为全量视图：上限 500 条防御超大项目；组内排序交由前端（状态分列后按活动时间）
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .orderByDesc(Issue::getUpdatedAt)
                .last("LIMIT 500");
        var issues = issueMapper.selectList(wrapper);
        var labelMap = labelsOf(issues.stream().map(Issue::getId).toList());
        return issues.stream()
                .map(i -> IssueVO.from(i, labelMap.getOrDefault(i.getId(), java.util.List.of())))
                .toList();
    }
    @Override
    public java.util.List<com.workflowx.issue.dto.TodoIssueVO> myTodoIssues(Long userId) {
        // 待办语义：指派给我且仍需我行动（OPEN/IN_PROGRESS/REOPENED）；RESOLVED 起等待他人
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getAssigneeId, userId)
                .in(Issue::getStatus, IssueStatus.OPEN, IssueStatus.IN_PROGRESS, IssueStatus.REOPENED)
                .orderByDesc(Issue::getPriority)
                .orderByDesc(Issue::getUpdatedAt)
                .last("LIMIT 20");
        java.util.List<Issue> issues = issueMapper.selectList(wrapper);
        if (issues.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<Long, com.workflowx.project.entity.Project> projects =
                projectMapper.selectBatchIds(issues.stream().map(Issue::getProjectId).toList())
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.workflowx.project.entity.Project::getId, java.util.function.Function.identity()));
        return issues.stream()
                .map(i -> {
                    com.workflowx.project.entity.Project project = projects.get(i.getProjectId());
                    return new com.workflowx.issue.dto.TodoIssueVO(
                            i.getId(), i.getProjectId(),
                            project == null ? null : project.getKey(),
                            project == null ? null : project.getName(),
                            i.getIssueNo(), i.getTitle(), i.getType(), i.getPriority(),
                            i.getSeverity(), i.getStatus(),
                            i.getUpdatedAt() == null ? null : i.getUpdatedAt().toString());
                })
                .toList();
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
        // V17 截止日期: null=不变；非空=设置；clearDueDate=true=清空（显式语义避免 null 二义）
        if (Boolean.TRUE.equals(request.clearDueDate())) {
            issue.setDueDate(null);
            issueMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Issue>()
                            .eq(Issue::getId, issueId)
                            .set(Issue::getDueDate, null));
        } else if (request.dueDate() != null) {
            issue.setDueDate(request.dueDate());
        }
        // V17 标签: null=不变；非空数组（含空）=全量替换
        if (request.labelIds() != null) {
            replaceLabels(projectId, issueId, request.labelIds());
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
        Issue fresh = requireIssue(projectId, issueId);
        return IssueVO.from(fresh,
                labelsOf(java.util.List.of(fresh.getId())).getOrDefault(fresh.getId(), java.util.List.of()));
    }


    private Issue requireIssue(Long projectId, Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        // 防跨项目访问: issue 必须归属于路径中的项目
        if (issue == null || !issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("issue", issueId);
        }
        return issue;
    }

    @Override
    public com.workflowx.common.web.PageVO<com.workflowx.issue.vo.WorkItemVO> pageMyWorkItems(
            Long userId, com.workflowx.issue.dto.WorkItemPageQuery query) {
        // 视图语义：all=全部可见 / assigned=指派给我 / todo=待我处理（同待办口径）/ created=我创建
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Issue> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Issue>();
        String scope = query.scope() == null ? "all" : query.scope();
        switch (scope) {
            case "assigned" -> wrapper.eq(Issue::getAssigneeId, userId);
            case "todo" -> {
                wrapper.eq(Issue::getAssigneeId, userId);
                wrapper.in(Issue::getStatus, IssueStatus.OPEN, IssueStatus.IN_PROGRESS, IssueStatus.REOPENED);
            }
            case "created" -> wrapper.eq(Issue::getReporterId, userId);
            default -> { /* all */ }
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            wrapper.and(w -> w.like(Issue::getTitle, query.keyword())
                    .or().like(Issue::getDescription, query.keyword()));
        }
        if (query.type() != null) {
            wrapper.eq(Issue::getType, query.type());
        }
        if (query.priority() != null) {
            wrapper.eq(Issue::getPriority, query.priority());
        }
        if (query.status() != null) {
            wrapper.eq(Issue::getStatus, query.status());
        }
        wrapper.orderByDesc(Issue::getUpdatedAt).orderByDesc(Issue::getId);
        Page<Issue> page = issueMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        var projects = page.getRecords().isEmpty()
                ? java.util.Map.<Long, com.workflowx.project.entity.Project>of()
                : projectMapper.selectBatchIds(page.getRecords().stream().map(Issue::getProjectId).distinct().toList())
                        .stream().collect(java.util.stream.Collectors.toMap(
                                com.workflowx.project.entity.Project::getId, pr -> pr));
        return PageVO.of(page.convert(i -> com.workflowx.issue.vo.WorkItemVO.of(i,
                projects.containsKey(i.getProjectId()) ? projects.get(i.getProjectId()).getKey() : null,
                projects.containsKey(i.getProjectId()) ? projects.get(i.getProjectId()).getName() : null)));
    }

    // ===== V17 工作项增强：标签 / 关联 =====

    /** 批量取标签（issueId → LabelVO 列表），两次查询避免 N+1 */
    private java.util.Map<Long, java.util.List<com.workflowx.issue.vo.LabelVO>> labelsOf(java.util.List<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return java.util.Map.of();
        }
        var bindings = issueLabelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.issue.entity.IssueLabel>()
                        .in(com.workflowx.issue.entity.IssueLabel::getIssueId, issueIds));
        if (bindings.isEmpty()) {
            return java.util.Map.of();
        }
        var labelIds = bindings.stream().map(com.workflowx.issue.entity.IssueLabel::getLabelId).distinct().toList();
        var labels = labelMapper.selectBatchIds(labelIds).stream()
                .collect(java.util.stream.Collectors.toMap(com.workflowx.issue.entity.Label::getId, l -> l));
        java.util.Map<Long, java.util.List<com.workflowx.issue.vo.LabelVO>> map = new java.util.HashMap<>();
        for (var b : bindings) {
            var label = labels.get(b.getLabelId());
            if (label != null) {
                map.computeIfAbsent(b.getIssueId(), k -> new java.util.ArrayList<>())
                        .add(com.workflowx.issue.vo.LabelVO.from(label));
            }
        }
        return map;
    }

    /** 全量替换工作项标签：先清后绑；标签须属于本项目（跨项目 404） */
    private void replaceLabels(Long projectId, Long issueId, java.util.List<Long> labelIds) {
        issueLabelMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.issue.entity.IssueLabel>()
                        .eq(com.workflowx.issue.entity.IssueLabel::getIssueId, issueId));
        if (labelIds == null || labelIds.isEmpty()) {
            return;
        }
        for (Long labelId : labelIds.stream().distinct().toList()) {
            var label = labelMapper.selectById(labelId);
            if (label == null || !label.getProjectId().equals(projectId)) {
                throw new com.workflowx.common.exception.ResourceNotFoundException("label", labelId);
            }
            var binding = new com.workflowx.issue.entity.IssueLabel();
            binding.setIssueId(issueId);
            binding.setLabelId(labelId);
            issueLabelMapper.insert(binding);
        }
    }

    @Override
    public java.util.List<com.workflowx.issue.vo.IssueLinkVO> listLinks(Long projectId, Long issueId) {
        Issue issue = requireIssue(projectId, issueId);
        java.util.List<com.workflowx.issue.vo.IssueLinkVO> result = new java.util.ArrayList<>();
        var outgoing = issueLinkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.issue.entity.IssueLink>()
                        .eq(com.workflowx.issue.entity.IssueLink::getSourceIssueId, issue.getId()));
        var incoming = issueLinkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.issue.entity.IssueLink>()
                        .eq(com.workflowx.issue.entity.IssueLink::getTargetIssueId, issue.getId()));
        appendLinkViews(result, outgoing, true);
        appendLinkViews(result, incoming, false);
        return result;
    }

    @Override
    public java.util.List<com.workflowx.issue.vo.IssueLinkVO> link(Long projectId, Long issueId,
            com.workflowx.issue.dto.CreateIssueLinkRequest request, Long operatorId) {
        Issue source = requireIssue(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        if (request.targetIssueId().equals(source.getId())) {
            throw new BusinessException(400, "不能与自身建立关联");
        }
        Issue target = requireIssue(projectId, request.targetIssueId());
        var type = request.linkType() == com.workflowx.issue.dto.CreateIssueLinkRequest.IssueLinkType.BLOCKS
                ? com.workflowx.issue.entity.IssueLink.LinkType.BLOCKS
                : com.workflowx.issue.entity.IssueLink.LinkType.RELATES;
        var existing = issueLinkMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.issue.entity.IssueLink>()
                        .eq(com.workflowx.issue.entity.IssueLink::getSourceIssueId, source.getId())
                        .eq(com.workflowx.issue.entity.IssueLink::getTargetIssueId, target.getId())
                        .eq(com.workflowx.issue.entity.IssueLink::getLinkType, type));
        if (existing != null) {
            throw new BusinessException(409, "两个工作项之间已存在该类型的关联");
        }
        var linkRow = new com.workflowx.issue.entity.IssueLink();
        linkRow.setSourceIssueId(source.getId());
        linkRow.setTargetIssueId(target.getId());
        linkRow.setLinkType(type);
        linkRow.setCreatedBy(operatorId);
        issueLinkMapper.insert(linkRow);
        auditService.record("ISSUE", "LINK",
                "issue:" + source.getId(), "关联 " + source.getIssueNo() + " → " + target.getIssueNo()
                        + "（" + type.name() + "）", true, operatorId);
        return listLinks(projectId, issueId);
    }

    @Override
    public void unlink(Long projectId, Long issueId, Long linkId, Long operatorId) {
        requireIssue(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        var linkRow = issueLinkMapper.selectById(linkId);
        if (linkRow == null || (!linkRow.getSourceIssueId().equals(issueId)
                && !linkRow.getTargetIssueId().equals(issueId))) {
            throw new com.workflowx.common.exception.ResourceNotFoundException("issue_link", linkId);
        }
        // 任一端的工作项成员均可解除（协作语义）；两端同项目已由创建约束保证
        issueLinkMapper.deleteById(linkId);
        auditService.record("ISSUE", "UNLINK", "issue:" + issueId, "解除关联 #" + linkId, true, operatorId);
    }

    private void appendLinkViews(java.util.List<com.workflowx.issue.vo.IssueLinkVO> sink,
            java.util.List<com.workflowx.issue.entity.IssueLink> links, boolean outgoing) {
        if (links.isEmpty()) {
            return;
        }
        var otherIds = links.stream()
                .map(l -> outgoing ? l.getTargetIssueId() : l.getSourceIssueId()).distinct().toList();
        var others = issueMapper.selectBatchIds(otherIds).stream()
                .collect(java.util.stream.Collectors.toMap(Issue::getId, i -> i));
        for (var l : links) {
            var other = others.get(outgoing ? l.getTargetIssueId() : l.getSourceIssueId());
            if (other != null) {
                sink.add(outgoing ? com.workflowx.issue.vo.IssueLinkVO.outgoing(l, other)
                        : com.workflowx.issue.vo.IssueLinkVO.incoming(l, other));
            }
        }
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
