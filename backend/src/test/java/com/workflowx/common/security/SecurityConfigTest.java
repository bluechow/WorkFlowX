package com.workflowx.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security 配置集成测试（P2-05）：完整上下文 + 真实过滤器链。
 * 使用独立测试密钥（不复用 dev 默认 secret）。
 * 未认证访问受保护端点 → 401 统一 JSON；有效 Bearer Token → 通过安全链进入分发（此路径无 Controller → 404）。
 */
@SpringBootTest(properties = "jwt.secret=test-only-secret-for-security-config-test-workflowx-0123456789")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthSessionService authSessionService;

    @Test
    void healthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void actuatorHealthShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401UnifiedJson() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("authentication required"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void validBearerTokenShouldPassSecurityChain() throws Exception {
        // P2-07 起 token 需同时具备有效 Redis 会话：签发 + 创建会话后再访问
        // P2-11 起 /api/v1/users 有真实 Controller 且 alice 具备 ADMIN 角色 → 返回 200 分页结构
        TokenIssuance issuance = jwtService.issueToken(42L, "alice", List.of("ADMIN"));
        authSessionService.createSession(42L, issuance.jti());
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + issuance.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void invalidBearerTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void malformedAuthorizationHeaderShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statelessShouldNotCreateHttpSession() throws Exception {
        var result = mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andReturn();
        assertTrue(result.getRequest().getSession(false) == null, "STATELESS 策略下不得创建 HttpSession");
        assertTrue(result.getResponse().getHeader("Set-Cookie") == null, "不得下发会话 Cookie");
    }

    @Test
    void formLoginShouldNotBeEnabled() throws Exception {
        // formLogin 未启用: /login 不会被重定向到登录页（302→login page），而是被 anyRequest().authenticated()
        // 拦截为 401 统一 JSON；若 formLogin 存在，未认证请求会 302 跳转登录页
        mockMvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Location", (String) null));
    }

    @Test
    void httpBasicShouldNotBeChallenged() throws Exception {
        // 未认证响应不得携带 Basic 质询头（未配置 httpBasic）
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", (String) null));
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
