package com.workflowx.auth.service;

import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.auth.dto.LoginResponse;

/**
 * 认证服务（P2-08）。
 * 登录流程: 查用户 → PasswordService.matches（统一错误防枚举）→ 状态检查（DISABLED/LOCKED 拒绝）
 * → JwtService 签发 → Redis 会话写入（fail-closed）→ 更新 last_login_at → 返回。
 * 失败计数/锁定（P2-13）、登出（P2-09）、/me（P2-10）不在本服务。
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);
}
