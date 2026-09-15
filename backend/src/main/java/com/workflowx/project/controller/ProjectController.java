package com.workflowx.project.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.dto.UpdateProjectRequest;
import com.workflowx.project.dto.UpdateProjectStatusRequest;
import com.workflowx.project.entity.ProjectStatus;
import com.workflowx.project.service.ProjectService;
import com.workflowx.project.vo.ProjectVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理接口（P5-02）。
 * 权限: hasAuthority（project:*，V7 种子）后端强制；
 * 数据级归属（ADR-014）: 写操作要求操作者为项目所属组织成员（Service 层 403）。
 * 无物理删除端点——归档代替（project:delete authority 预留）。
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("hasAuthority('project:list')")
    public Result<PageVO<ProjectVO>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) ProjectStatus status,
                                          @RequestParam(required = false) Long orgId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return Result.ok(projectService.page(keyword, status, orgId,
                page == null ? 1 : page, size == null ? 20 : size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:create')")
    public ResponseEntity<Result<ProjectVO>> create(@Valid @RequestBody CreateProjectRequest request,
                                                    @AuthenticationPrincipal JwtPayload operator) {
        ProjectVO created = projectService.create(request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:get')")
    public Result<ProjectVO> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public Result<ProjectVO> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateProjectRequest request,
                                    @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(projectService.update(id, request, operator.userId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('project:update')")
    public Result<ProjectVO> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateProjectStatusRequest request,
                                          @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(projectService.updateStatus(id, request.status(), operator.userId()));
    }
}
