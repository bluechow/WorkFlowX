package com.workflowx.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（P2-05 建立，P2-11 启用方法级安全）。
 * - 无状态（STATELESS，不创建/不使用 HttpSession）
 * - CSRF 关闭（纯 REST API + Bearer Token，无 Cookie 会话可被 CSRF 利用途）
 * - 未配置 formLogin / httpBasic（不存在登录页与 Basic 质询）
 * - 除公共端点外一律要求认证；认证失败由 RestAuthenticationEntryPoint 返回统一 401 JSON
 * - P2-11: @EnableMethodSecurity 支持 @PreAuthorize("hasRole('ADMIN')") 后端强制权限（Master Prompt §7）
 * - JwtAuthenticationFilter 在 SecurityConfig 内部构造（非 Bean），避免 Boot 重复注册 Filter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /** 公开端点（健康检查 / 文档 / 登录） */
    public static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/health",
            "/actuator/health",
            "/api/v1/auth/login",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final com.workflowx.rbac.service.PermissionService permissionService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, authSessionService, permissionService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
