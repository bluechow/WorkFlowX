package com.workflowx.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（P2-06）。
 * 职责仅限: 提取 Bearer Token → JwtService 验证 → 构建 Authentication（principal=JwtPayload，
 * authorities=ROLE_{role}，与后续 RBAC 的 hasRole 对齐）→ 写入 SecurityContext。
 * 验证失败时不抛出/不泄漏原因，仅不建立认证，由 EntryPoint 统一返回 401。
 * 不做数据库查询 / Redis 会话 / 登录计数 / 用户状态变更（分属 P2-07 与后续任务）。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                JwtPayload payload = jwtService.parseToken(token);
                List<SimpleGrantedAuthority> authorities = payload.roles().stream()
                        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(payload, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // 无效 token（篡改/过期/格式错误/缺失 claim）：清除上下文即可，不向客户端泄漏细节
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
