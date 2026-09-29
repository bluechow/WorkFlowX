package com.workflowx.issue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateCommentRequest;
import com.workflowx.issue.dto.UpdateCommentRequest;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueComment;
import com.workflowx.issue.mapper.CommentMapper;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.CommentService;
import com.workflowx.issue.vo.CommentVO;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评论领域实现（P8-03；ADR-018）。
 * 校验链: project 404 → issue 404（防跨项目 ID 拼接）→ 项目成员 403 → ownership 403（编辑/删除）。
 * authority 校验在 Controller @PreAuthorize；本类负责数据级与业务规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final com.workflowx.notification.service.NotificationService notificationService;
    private final com.workflowx.user.mapper.UserMapper userMapper;
    private final com.workflowx.audit.service.AuditService auditService;
    private final com.workflowx.activity.service.ActivityService activityService;

    @Override
    public CommentVO create(Long projectId, Long issueId, CreateCommentRequest request, Long operatorId) {
        Issue issue = requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        IssueComment comment = new IssueComment();
        comment.setIssueId(issueId);
        comment.setAuthorId(operatorId);
        comment.setContent(request.content());
        commentMapper.insert(comment);
        // P9-07: 新评论 → 通知 assignee + reporter（排除评论者本人，Set 去重；共事务）
        java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (issue.getAssigneeId() != null) {
            recipients.add(issue.getAssigneeId());
        }
        recipients.add(issue.getReporterId());
        recipients.remove(operatorId);
        String projectKey = projectMapper.selectById(projectId).getKey();
        if (!recipients.isEmpty()) {
            for (Long recipientId : recipients) {
                notificationService.notifyIssueCommented(projectKey, issue.getIssueNo(),
                        issue.getTitle(), issueId, recipientId, operatorId);
            }
        }
        // FP-7: @用户名/@昵称 → ISSUE_MENTIONED（与评论通知去重；限定项目成员——不越权提及非成员）
        java.util.List<Long> mentioned = resolveMentions(request.content(), projectId);
        mentioned.removeAll(recipients);
        mentioned.remove(operatorId);
        if (!mentioned.isEmpty()) {
            notificationService.notifyIssueMentioned(projectKey, issue.getIssueNo(),
                    issue.getTitle(), issueId, mentioned, operatorId);
        }
        auditService.record("COMMENT", "CREATE", "issue:" + issueId,
                "评论 Issue " + issue.getIssueNo(), true, operatorId);
        activityService.record(projectId, issueId, operatorId, com.workflowx.activity.entity.Activity.Action.COMMENT,
                "issue:" + issueId, "添加了评论");
        return CommentVO.from(commentMapper.selectById(comment.getId()));
    }

    @Override
    public PageVO<CommentVO> page(Long projectId, Long issueId, long page, long size, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        LambdaQueryWrapper<IssueComment> wrapper = new LambdaQueryWrapper<IssueComment>()
                .eq(IssueComment::getIssueId, issueId)
                .orderByAsc(IssueComment::getCreatedAt)
                .orderByAsc(IssueComment::getId);
        IPage<IssueComment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageVO.of(result.convert(CommentVO::from));
    }

    @Override
    public CommentVO getById(Long projectId, Long issueId, Long commentId, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        return CommentVO.from(requireCommentInIssue(issueId, commentId));
    }

    @Override
    public CommentVO update(Long projectId, Long issueId, Long commentId, UpdateCommentRequest request, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        IssueComment comment = requireCommentInIssue(issueId, commentId);
        requireAuthor(comment, operatorId);
        comment.setContent(request.content());
        commentMapper.updateById(comment);
        return CommentVO.from(commentMapper.selectById(commentId));
    }

    @Override
    public void delete(Long projectId, Long issueId, Long commentId, Long operatorId) {
        requireIssueInProject(projectId, issueId);
        requireProjectMembership(projectId, operatorId);
        IssueComment comment = requireCommentInIssue(issueId, commentId);
        requireAuthor(comment, operatorId);
        commentMapper.deleteById(commentId);
        auditService.record("COMMENT", "DELETE", "issue:" + issueId,
                "删除评论 " + commentId, true, operatorId);
    }

    /** comment 必须属于指定 issue，跨 issue/跨项目访问一律 404（不泄露存在性）。 */
    private IssueComment requireCommentInIssue(Long issueId, Long commentId) {
        IssueComment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getIssueId().equals(issueId)) {
            throw new ResourceNotFoundException("comment", commentId);
        }
        return comment;
    }

    /** ownership: 仅作者本人可编辑/删除；ADMIN 非 owner 同样 403（ADR-018，对齐 Phase 4/5 先例）。 */
    private void requireAuthor(IssueComment comment, Long operatorId) {
        if (!comment.getAuthorId().equals(operatorId)) {
            throw new ForbiddenException("仅评论作者可操作该评论");
        }
    }

    private Issue requireIssueInProject(Long projectId, Long issueId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || !issue.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("issue", issueId);
        }
        return issue;
    }

    private void requireProjectMembership(Long projectId, Long operatorId) {
        if (projectMemberMapper.findMember(projectId, operatorId) == null) {
            throw new ForbiddenException("仅项目成员可访问该项目的评论");
        }
    }

    /** 解析评论中的 @提及（@用户名 或 @昵称，忽略大小写），限定项目成员范围 */
    private java.util.List<Long> resolveMentions(String content, Long projectId) {
        java.util.List<Long> hits = new java.util.ArrayList<>();
        if (content == null || !content.contains("@")) {
            return hits;
        }
        String lower = content.toLowerCase();
        for (var membership : projectMemberMapper.findMembersByProjectId(projectId)) {
            var user = userMapper.selectById(membership.getUserId());
            if (user == null) {
                continue;
            }
            boolean byUsername = lower.contains("@" + user.getUsername().toLowerCase());
            boolean byNickname = user.getNickname() != null
                    && lower.contains("@" + user.getNickname().toLowerCase());
            if (byUsername || byNickname) {
                hits.add(user.getId());
            }
        }
        return hits;
    }
}
