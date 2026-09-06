package com.workflowx.auth.controller;

import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.auth.dto.LoginResponse;
import com.workflowx.auth.service.AuthService;
import com.workflowx.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（P2-08）。
 * POST /api/v1/auth/login 为公开端点（SecurityConfig 白名单）；logout（P2-09）与 me（P2-10）后续加入。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
