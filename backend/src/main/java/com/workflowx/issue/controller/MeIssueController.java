package com.workflowx.issue.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.issue.dto.TodoIssueVO;
import com.workflowx.issue.service.IssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的跨项目 Issue 视图（Phase A-④）：self 资源（对齐 /auth/me 先例，无 authority 码），
 * userId 一律取自 SecurityContext，不接受客户端传入。
 */
@Tag(name = "Me Issues", description = "我的待办（跨项目，self 资源）")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeIssueController {

    private final IssueService issueService;

    @Operation(summary = "我的待办：指派给我且未完结的 Issue（上限 20）")
    @GetMapping("/todo-issues")
    public Result<List<TodoIssueVO>> myTodoIssues(@AuthenticationPrincipal JwtPayload principal) {
        return Result.ok(issueService.myTodoIssues(principal.userId()));
    }

    /** 全局工作项视图（V17）：scope=all/assigned/todo/created；读语义=issue:list authority */
    @Operation(summary = "全局工作项分页（全部/我的/待我处理/我创建的）")
    @GetMapping("/work-items")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('issue:list')")
    public Result<com.workflowx.common.web.PageVO<com.workflowx.issue.vo.WorkItemVO>> myWorkItems(
            @AuthenticationPrincipal JwtPayload principal,
            @jakarta.validation.Valid com.workflowx.issue.dto.WorkItemPageQuery query) {
        return Result.ok(issueService.pageMyWorkItems(principal.userId(), query));
    }
}
