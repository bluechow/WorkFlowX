package com.workflowx.user.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.dto.UserPageQuery;
import com.workflowx.user.service.UserService;
import com.workflowx.user.vo.UserVO;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（P2-11 CRUD + P2-12 启用/禁用，P3-03 权限细化）。
 * 权限模型: 细粒度 authority（user:list/get/create/update/status）后端强制——authorities 由
 * JwtAuthenticationFilter 实时解析（user→role→permission），收权即时生效（ADR-012）。
 * 未认证 → 401；已认证无对应权限 → 403。Controller 仅做参数/DTO/状态码，业务在 UserService。
 * 文档注解: 类级 bearerAuth 显式声明，OpenAPI 中与运行时鉴权对应。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public Result<com.workflowx.common.web.PageVO<UserVO>> page(@Valid UserPageQuery query) {
        return Result.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:get')")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<Result<UserVO>> create(@Valid @RequestBody CreateUserRequest request) {
        UserVO created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:status')")
    public Result<UserVO> updateStatus(@AuthenticationPrincipal JwtPayload operator,
                                       @PathVariable Long id,
                                       @Valid @RequestBody com.workflowx.user.dto.UpdateUserStatusRequest request) {
        return Result.ok(userService.updateStatus(operator.userId(), id, request.status()));
    }
}
