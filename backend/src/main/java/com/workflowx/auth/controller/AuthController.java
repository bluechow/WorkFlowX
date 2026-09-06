package com.workflowx.auth.controller;

import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.auth.dto.LoginResponse;
import com.workflowx.auth.service.AuthService;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.user.vo.UserVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（P2-08 login，P2-09 logout，P2-10 me），统一入口 /api/v1/auth/*。
 * login 为公开端点（SecurityConfig 白名单）；logout 与 me 需要认证。
 * userId 一律取自 SecurityContext 的 JwtPayload，不接受客户端传入（防越权删除/查看他人会话与信息）。
 * 文档注解（P2-14 修正）: login 空 @SecurityRequirements 标记公开；logout/me 显式 bearerAuth，
 * 与运行时鉴权（白名单/认证必需）一一对应。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public Result<Void> logout(@AuthenticationPrincipal JwtPayload principal) {
        authService.logout(principal.userId());
        return Result.ok();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public Result<UserVO> me(@AuthenticationPrincipal JwtPayload principal) {
        return Result.ok(authService.getCurrentUser(principal.userId()));
    }
}
