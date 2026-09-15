package com.workflowx.project.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.project.dto.AddProjectMemberRequest;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.vo.ProjectMemberVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目成员接口（P5-04）。
 * 权限: 查看 project:get；管理 project:assign_member（hasAuthority 后端强制）；
 * 数据级: 添加/移除的操作者须为项目所属组织成员（Service 层 403，ADR-014/015）。
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/members")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    @PreAuthorize("hasAuthority('project:get')")
    public Result<List<ProjectMemberVO>> list(@PathVariable Long id) {
        return Result.ok(projectMemberService.listMembers(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:assign_member')")
    public Result<ProjectMemberVO> add(@PathVariable Long id,
                                       @Valid @RequestBody AddProjectMemberRequest request,
                                       @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(projectMemberService.addMember(id, request, operator.userId()));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('project:assign_member')")
    public Result<Void> remove(@PathVariable Long id, @PathVariable Long userId,
                               @AuthenticationPrincipal JwtPayload operator) {
        projectMemberService.removeMember(id, userId, operator.userId());
        return Result.ok();
    }
}
