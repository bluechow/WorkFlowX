package com.workflowx.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（P2-06 建立，P2-07 会话校验，P3-03 权限 authorities 接线）。
 * 链路: Bearer Token → JWT 密码学验证 → Redis 会话比对 → authorities 构建：
 *   1) roles claim → ROLE_{code}（角色 authority，兼容 hasRole）
 *   2) PermissionService 实时查询 user→role→permission → 权限 authority（无前缀，配 hasAuthority）
 * 权限实时解析保证收权即时生效（ADR-012）；DB/Redis 异常 fail-closed（不建立认证，拒绝访问）。
 * 失败不向客户端泄漏原因，不打印完整 token/secret。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final com.workflowx.rbac.service.PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                JwtPayload payload = jwtService.parseToken(token);
                if (authSessionService.isCurrentSession(payload.userId(), payload.jti())) {
                    List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                    payload.roles().stream()
                            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                            .forEach(authorities::add);
                    // 权限实时解析（user→role→permission）：收权/授权即时生效，无需重签 token
                    permissionService.findPermissionCodesByUserId(payload.userId()).stream()
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);
                    var authentication = new UsernamePasswordAuthenticationToken(payload, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // token 无效（篡改/过期/格式错误/缺失 claim）：不建立认证，不泄漏细节
            SecurityContextHolder.clearContext();
        } catch (DataAccessException e) {
            // Redis/MySQL 不可用: fail-closed——认证无法确认会话/权限即拒绝访问，服务端仅记录摘要信息
            log.warn("auth check failed (fail-closed): {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无状态会话：请求结束即清理上下文，不残留线程绑定数据
            SecurityContextHolder.clearContext();
        }
    }
}
