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
 * JWT 认证过滤器（P2-06 建立，P2-07 增加会话校验）。
 * 链路: Bearer Token → JWT 密码学验证 → 取 userId+jti → Redis 会话比对 → 一致才建立 Authentication。
 * 单会话模型: 后登录覆盖先登录后，旧 token 的 jti 不再匹配，即使签名有效也被拒绝。
 * fail-closed: Redis 不可用/会话不存在/任何验证失败 → 不建立认证（401），服务端记日志但不向客户端泄漏原因，
 * 也不打印完整 token/secret。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                JwtPayload payload = jwtService.parseToken(token);
                if (authSessionService.isCurrentSession(payload.userId(), payload.jti())) {
                    List<SimpleGrantedAuthority> authorities = payload.roles().stream()
                            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(payload, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // token 无效（篡改/过期/格式错误/缺失 claim）：不建立认证，不泄漏细节
            SecurityContextHolder.clearContext();
        } catch (DataAccessException e) {
            // Redis 不可用: fail-closed——认证无法确认会话即拒绝访问，服务端仅记录摘要信息
            log.warn("auth session check failed (fail-closed): {}", e.getClass().getSimpleName());
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
