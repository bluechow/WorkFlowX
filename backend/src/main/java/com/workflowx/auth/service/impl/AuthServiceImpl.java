package com.workflowx.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.auth.dto.LoginResponse;
import com.workflowx.auth.mapper.AuthRoleQueryMapper;
import com.workflowx.auth.service.AuthService;
import com.workflowx.auth.service.LoginAttemptService;
import com.workflowx.common.exception.AuthenticationException;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.common.security.JwtProperties;
import com.workflowx.common.security.JwtService;
import com.workflowx.common.security.TokenIssuance;
import com.workflowx.user.entity.User;
import com.workflowx.user.entity.UserStatus;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.PasswordService;
import com.workflowx.user.service.UserService;
import com.workflowx.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现（P2-08）。
 * 错误策略: 用户不存在与密码错误统一为 401「用户名或密码错误」（防用户枚举）；
 * 状态检查放在密码验证**之后**——否则攻击者可借助状态差异消息探测任意 username 是否存在。
 * 一致性: 只有密码验证成功才签发 JWT 并写 Redis；Redis 写入失败则登录失败（fail-closed，不返回无法通过
 * 认证链的 token）；last_login_at 更新失败不吞异常（可能留下 ≤2h 过期的孤儿会话，无越权风险）。
 * 不打印密码/token/secret。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final AuthRoleQueryMapper authRoleQueryMapper;
    private final JwtProperties jwtProperties;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // P2-13: 锁定检查最前——锁定期间不查库、不验密码、不签发 token、不建会话
        if (loginAttemptService.isLocked(request.username())) {
            throw new BusinessException(429, "登录尝试次数过多，请稍后再试");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            // 统一计数（含不存在的 username）: 防止 429 仅出现在真实用户名上造成账号枚举泄漏（ADR-010）
            long failures = loginAttemptService.recordFailure(request.username());
            if (failures >= loginAttemptService.MAX_ATTEMPTS) {
                throw new BusinessException(429, "登录尝试次数过多，请稍后再试");
            }
            // 统一错误信息: 不区分"用户不存在"与"密码错误"
            throw new AuthenticationException("用户名或密码错误");
        }
        loginAttemptService.clearFailures(request.username());
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(403, "账号已被锁定，请稍后重试");
        }

        List<String> roles = authRoleQueryMapper.findRoleCodesByUserId(user.getId());
        TokenIssuance issuance = jwtService.issueToken(user.getId(), user.getUsername(), roles);
        // fail-closed: 会话写入失败将抛出异常，登录即失败，不会返回无法通过认证链的 token
        authSessionService.createSession(user.getId(), issuance.jti());

        User lastLoginUpdate = new User();
        lastLoginUpdate.setId(user.getId());
        lastLoginUpdate.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(lastLoginUpdate);
        log.info("user logged in: userId={}", user.getId());

        long expiresIn = jwtProperties.getExpireHours() * 3600L;
        return new LoginResponse(issuance.accessToken(), "Bearer", expiresIn, user.getId(), user.getUsername(), roles);
    }

    @Override
    public void logout(Long userId) {
        // 幂等: 会话不存在时 Redis DEL 为空操作，不抛异常；原 Token 因会话缺失在 Filter 层即 401
        authSessionService.deleteSession(userId);
        log.info("user logged out: userId={}", userId);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        // 数据来源为数据库而非 JWT claims: email/nickname 等变更后 /me 立即反映最新值；
        // 会话有效但用户已被删除 → ResourceNotFoundException(404)，绝不返回 200 + null
        return userService.getById(userId);
    }
}
