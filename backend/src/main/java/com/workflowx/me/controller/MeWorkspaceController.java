package com.workflowx.me.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.activity.entity.Activity;
import com.workflowx.activity.mapper.ActivityMapper;
import com.workflowx.activity.vo.ActivityVO;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.mapper.IssueMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作台聚合（Final Edition FP-5）：self 资源（对齐 /auth/me 先例，无 authority 码）。
 * - /me/projects：我加入的项目（成员视角；不受 project:list 限制——自己的项目自己可见）
 * - /me/activities：我最近操作过的动态（跨项目）
 * - /me/work-summary：我的工作项三视图计数（assigned/todo/created）
 */
@Tag(name = "Me Workspace", description = "我的工作台聚合（self 资源）")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MeWorkspaceController {

    private final com.workflowx.project.mapper.ProjectMemberMapper projectMemberMapper;
    private final com.workflowx.project.mapper.ProjectMapper projectMapper;
    private final ActivityMapper activityMapper;
    private final IssueMapper issueMapper;

    public record MyProjectVO(Long id, String key, String name, String status, String myRole, Long ownerId) {
    }

    @Operation(summary = "我加入的项目（成员视角）")
    @GetMapping("/projects")
    public Result<List<MyProjectVO>> myProjects(@AuthenticationPrincipal JwtPayload principal) {
        var memberships = projectMemberMapper.findMembersByUserId(principal.userId());
        if (memberships.isEmpty()) {
            return Result.ok(List.of());
        }
        var projects = projectMapper.selectBatchIds(
                        memberships.stream().map(com.workflowx.project.entity.ProjectMember::getProjectId).toList())
                .stream().collect(Collectors.toMap(com.workflowx.project.entity.Project::getId, p -> p));
        var roleByProject = memberships.stream().collect(Collectors.toMap(
                com.workflowx.project.entity.ProjectMember::getProjectId,
                m -> m.getRole().name(), (a, b) -> a));
        return Result.ok(memberships.stream()
                .map(m -> {
                    var p = projects.get(m.getProjectId());
                    return p == null ? null : new MyProjectVO(p.getId(), p.getKey(), p.getName(),
                            p.getStatus().name(), roleByProject.get(p.getId()), p.getOwnerId());
                })
                .filter(vo -> vo != null).toList());
    }

    @Operation(summary = "我最近操作过的动态（跨项目，倒序）")
    @GetMapping("/activities")
    public Result<List<ActivityVO>> myActivities(@AuthenticationPrincipal JwtPayload principal,
                                                 @RequestParam(defaultValue = "10") int limit) {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getActorId, principal.userId())
                        .orderByDesc(Activity::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        if (activities.isEmpty()) {
            return Result.ok(List.of());
        }
        List<Long> issueIds = activities.stream()
                .map(Activity::getIssueId).filter(id -> id != null).distinct().toList();
        Map<Long, Long> issueNoById = issueIds.isEmpty() ? Map.of()
                : issueMapper.selectBatchIds(issueIds).stream()
                        .collect(Collectors.toMap(Issue::getId, Issue::getIssueNo));
        return Result.ok(activities.stream()
                .map(a -> ActivityVO.of(a, a.getIssueId() == null ? null : issueNoById.get(a.getIssueId())))
                .toList());
    }

    public record WorkSummaryVO(long assigned, long todo, long created) {
    }

    @Operation(summary = "我的工作项三视图计数")
    @GetMapping("/work-summary")
    public Result<WorkSummaryVO> workSummary(@AuthenticationPrincipal JwtPayload principal) {
        long userId = principal.userId();
        long assigned = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getAssigneeId, userId));
        long todo = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getAssigneeId, userId)
                .in(Issue::getStatus, IssueStatus.OPEN, IssueStatus.IN_PROGRESS, IssueStatus.REOPENED));
        long created = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getReporterId, userId));
        return Result.ok(new WorkSummaryVO(assigned, todo, created));
    }
}
