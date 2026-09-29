package com.workflowx.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.mapper.ProjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据分析（Final Edition FP-7）：成员工作量 + Bug 专项。
 * 权限=dashboard:view（与总览一致）；数据范围为 ADMIN 全局 / 非管理员我的项目（复用总览数据范围语义）。
 */
@Tag(name = "Analytics", description = "数据分析（FP-7：成员工作量 / Bug 专项）")
@RestController
@RequestMapping("/api/v1/dashboard/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;
    private final com.workflowx.project.mapper.ProjectMemberMapper projectMemberMapper;
    private final com.workflowx.user.mapper.UserMapper userMapper;

    public record MemberLoadVO(Long userId, String username, String nickname,
                                long assigned, long open, long bugs) {
    }

    public record BugBreakdownVO(long total, Map<String, Long> bySeverity, Map<String, Long> byStatus) {
    }

    public record AnalyticsVO(List<MemberLoadVO> members, BugBreakdownVO bugs) {
    }

    @Operation(summary = "团队分析：成员工作量 + Bug 专项（数据范围同 dashboard 总览）")
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<AnalyticsVO> analytics(@AuthenticationPrincipal JwtPayload operator,
                                         @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>();
        // 数据范围：ADMIN 全局；非管理员限定我加入的项目（与 DashboardServiceImpl 同语义）
        boolean isAdmin = operator.roles() != null && operator.roles().contains("ADMIN");
        if (projectId != null) {
            wrapper.eq(Issue::getProjectId, projectId);
        } else if (!isAdmin) {
            List<Long> myProjectIds = projectMemberMapper.findAllProjectIdsByUserId(operator.userId());
            if (myProjectIds.isEmpty()) {
                return Result.ok(new AnalyticsVO(List.of(),
                        new BugBreakdownVO(0, Map.of(), Map.of())));
            }
            wrapper.in(Issue::getProjectId, myProjectIds);
        }
        List<Issue> issues = issueMapper.selectList(wrapper);
        if (issues.isEmpty()) {
            return Result.ok(new AnalyticsVO(List.of(),
                    new BugBreakdownVO(0, Map.of(), Map.of())));
        }

        // 用户名/昵称回填（按经办人批量取）
        var userById = issues.stream()
                .map(Issue::getAssigneeId).filter(id -> id != null).distinct().toList()
                .isEmpty() ? java.util.Map.<Long, com.workflowx.user.entity.User>of()
                : userMapper.selectBatchIds(issues.stream()
                        .map(Issue::getAssigneeId).filter(id -> id != null).distinct().toList())
                        .stream().collect(Collectors.toMap(com.workflowx.user.entity.User::getId, u -> u));

        Map<String, Long> bySeverity = issues.stream()
                .filter(i -> i.getSeverity() != null)
                .collect(Collectors.groupingBy(i -> i.getSeverity().name(), Collectors.counting()));
        Map<String, Long> bugByStatus = issues.stream()
                .filter(i -> "BUG".equals(i.getType() == null ? "" : i.getType().name()))
                .collect(Collectors.groupingBy(i -> i.getStatus().name(), Collectors.counting()));

        // 成员工作量：按 assignee 聚合（open=未完结；bugs=缺陷数）
        Map<Long, List<Issue>> byAssignee = issues.stream()
                .filter(i -> i.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Issue::getAssigneeId));
        List<MemberLoadVO> members = byAssignee.entrySet().stream()
                .map(e -> {
                    long assigned = e.getValue().size();
                    long open = e.getValue().stream()
                            .filter(i -> i.getStatus() == com.workflowx.issue.entity.IssueStatus.OPEN
                                    || i.getStatus() == com.workflowx.issue.entity.IssueStatus.IN_PROGRESS
                                    || i.getStatus() == com.workflowx.issue.entity.IssueStatus.REOPENED)
                            .count();
                    long bugs = e.getValue().stream()
                            .filter(i -> "BUG".equals(i.getType() == null ? "" : i.getType().name()))
                            .count();
                    var u = userById.get(e.getKey());
                    return new MemberLoadVO(e.getKey(),
                            u == null ? null : u.getUsername(),
                            u == null ? null : u.getNickname(),
                            assigned, open, bugs);
                })
                .sorted((a, b) -> Long.compare(b.assigned(), a.assigned()))
                .limit(30)
                .toList();

        long bugTotal = issues.stream()
                .filter(i -> "BUG".equals(i.getType() == null ? "" : i.getType().name())).count();
        return Result.ok(new AnalyticsVO(members, new BugBreakdownVO(bugTotal, bySeverity, bugByStatus)));
    }
}
