package com.workflowx.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
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

/**
 * 全局搜索（Final Edition FP-6）。
 * 权限语义（不绕过任何既有边界）：
 * - 项目：project:list authority 者可见全部匹配；无权限者不返回项目结果；
 * - 工作项：issue:list authority 者可见匹配项（与项目内列表读语义一致——authority-only）；
 * - 用户：user:list authority 者可见（用户名/昵称模糊）。
 * 每类最多 8 条；keyword 为空返回空集。
 */
@Tag(name = "Search", description = "全局搜索（FP-6）")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private static final int LIMIT = 8;

    private final ProjectMapper projectMapper;
    private final IssueMapper issueMapper;
    private final UserMapper userMapper;

    public record ProjectHit(Long id, String key, String name) {
    }

    public record IssueHit(Long id, Long projectId, String projectKey, Long issueNo, String title, String status) {
    }

    public record UserHit(Long id, String username, String nickname) {
    }

    public record SearchResultVO(List<ProjectHit> projects, List<IssueHit> issues, List<UserHit> users) {
    }

    /** 关键词长度上限（防超长 LIKE 拖库；客户端防抖不保护直接 API 调用） */
    private static final int MAX_KEYWORD = 64;

    @Operation(summary = "全局搜索：项目/工作项/用户（按权限范围收敛；关键词≤64 字符）")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<SearchResultVO> search(@RequestParam(required = false) String keyword,
                                         org.springframework.security.core.Authentication authentication) {
        if (keyword == null || keyword.isBlank()) {
            return Result.ok(new SearchResultVO(List.of(), List.of(), List.of()));
        }
        if (keyword.length() > MAX_KEYWORD) {
            throw new com.workflowx.common.exception.BusinessException(422, "关键词最长 64 字符");
        }
        String kw = keyword.trim();
        // 权限范围与 @PreAuthorize 同源（Filter 实时装配的 authorities）
        var authorities = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());

        List<ProjectHit> projects = List.of();
        if (authorities.contains("project:list")) {
            projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .and(w -> w.like(Project::getName, kw).or().like(Project::getKey, kw))
                            .last("LIMIT " + LIMIT))
                    .stream().map(p -> new ProjectHit(p.getId(), p.getKey(), p.getName())).toList();
        }

        List<IssueHit> issues = List.of();
        if (authorities.contains("issue:list")) {
            var matched = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                    .like(Issue::getTitle, kw)
                    .last("LIMIT " + LIMIT));
            if (!matched.isEmpty()) {
                var keyById = projectMapper.selectBatchIds(
                                matched.stream().map(Issue::getProjectId).distinct().toList())
                        .stream().collect(java.util.stream.Collectors.toMap(Project::getId, Project::getKey));
                issues = matched.stream()
                        .map(i -> new IssueHit(i.getId(), i.getProjectId(),
                                keyById.get(i.getProjectId()), i.getIssueNo(),
                                i.getTitle(), i.getStatus().name()))
                        .toList();
            }
        }

        List<UserHit> users = List.of();
        if (authorities.contains("user:list")) {
            users = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw))
                            .last("LIMIT " + LIMIT))
                    .stream().map(u -> new UserHit(u.getId(), u.getUsername(), u.getNickname())).toList();
        }

        return Result.ok(new SearchResultVO(projects, issues, users));
    }
}
