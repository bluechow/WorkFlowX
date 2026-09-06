package com.workflowx.user.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
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
 * 用户管理接口（P2-11 CRUD + P2-12 启用/禁用）。
 * 权限模型: 全部端点要求认证 + ADMIN 角色（@PreAuthorize 后端强制，Master Prompt §7）；
 * 未认证 → 401；已认证非 ADMIN → 403。
 * Controller 仅做参数/DTO/状态码，业务在 UserService，踢线在禁用转换内完成。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<com.workflowx.common.web.PageVO<UserVO>> page(@Valid UserPageQuery query) {
        return Result.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<UserVO>> create(@Valid @RequestBody CreateUserRequest request) {
        UserVO created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserVO> updateStatus(@AuthenticationPrincipal JwtPayload operator,
                                       @PathVariable Long id,
                                       @Valid @RequestBody com.workflowx.user.dto.UpdateUserStatusRequest request) {
        return Result.ok(userService.updateStatus(operator.userId(), id, request.status()));
    }
}
