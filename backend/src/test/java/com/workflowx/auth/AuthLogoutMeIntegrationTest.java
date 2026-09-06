package com.workflowx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.common.security.JwtService;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.entity.UserStatus;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登出与 /me 集成测试（P2-09 + P2-10）：真实 MySQL + 真实 Redis（compose 基础设施）。
 * 数据隔离: 用户统一 p2_auth_test_ 前缀，用后清理用户（user_roles 级联）与 Redis 会话键。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthLogoutMeIntegrationTest {

    private static final String PREFIX = "p2_auth_test_";
    private static final String PASSWORD = "AuthPass@123";
    private static final String SESSION_KEY_PREFIX = "auth:session:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete(SESSION_KEY_PREFIX + id);
        }
        createdUserIds.clear();
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, PREFIX));
    }

    private User createTestUser(String suffix, UserStatus status) {
        var created = userService.create(new CreateUserRequest(
                PREFIX + suffix, PREFIX + suffix + "@test.local", PASSWORD, "auth-" + suffix));
        createdUserIds.add(created.id());
        if (status != UserStatus.ACTIVE) {
            userService.updateStatus(created.id(), status);
        }
        return userMapper.selectById(created.id());
    }

    private String loginAndGetToken(String username) throws Exception {
        String body = """
                {"username": %s, "password": %s}
                """.formatted(objectMapper.valueToTree(username), objectMapper.valueToTree(PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return (String) data.get("accessToken");
    }

    // ===== Logout =====

    @Test
    void logoutShouldDeleteSessionAndInvalidateTokenImmediately() throws Exception {
        User user = createTestUser("lo", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "lo");

        String sessionKey = SESSION_KEY_PREFIX + user.getId();
        assertNotNull(redisTemplate.opsForValue().get(sessionKey), "登出前会话应存在");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertTrue(redisTemplate.opsForValue().get(sessionKey) == null, "登出后 Redis 会话必须被删除");
        // 原 Token 立即失效（受保护 API 与 /me 均拒绝）
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLogoutWithSameTokenShouldReturn401BecauseSessionGone() throws Exception {
        createTestUser("lo2", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "lo2");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 会话已删除，同一 token 不再能认证——401 即"幂等语义下的正确终态"；
        // Service 层 deleteSession 对不存在的 key 幂等（不抛异常）
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        authSessionService.deleteSession(999999999L);
        authSessionService.deleteSession(999999999L);
    }

    @Test
    void logoutWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void logoutShouldOnlyAffectCurrentUsersSession() throws Exception {
        User userA = createTestUser("lo-a", UserStatus.ACTIVE);
        User userB = createTestUser("lo-b", UserStatus.ACTIVE);
        String tokenA = loginAndGetToken(PREFIX + "lo-a");
        String tokenB = loginAndGetToken(PREFIX + "lo-b");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // A 的会话删除、B 的会话不受影响（无任何按 userId 越权入口）
        assertTrue(redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userA.getId()) == null);
        assertNotNull(redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userB.getId()));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(PREFIX + "lo-b"));
    }

    // ===== /me =====

    @Test
    void meShouldReturnCurrentDatabaseUserInfo() throws Exception {
        User user = createTestUser("me", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "me");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.nickname").value(user.getNickname()));
    }

    @Test
    void meShouldReflectLatestDatabaseChanges() throws Exception {
        User user = createTestUser("fresh", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "fresh");
        // 登录后修改数据库: /me 必须返回最新值（数据来自 DB 而非 JWT claims）
        userService.update(user.getId(), new com.workflowx.user.dto.UpdateUserRequest(
                PREFIX + "fresh-new@test.local", "updated-nick"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(PREFIX + "fresh-new@test.local"))
                .andExpect(jsonPath("$.data.nickname").value("updated-nick"));
    }

    @Test
    void meResponseMustNotContainSensitiveFields() throws Exception {
        createTestUser("sensitive", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "sensitive");
        String body = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(!body.contains("password") && !body.contains("password_hash"), "不得包含密码字段: " + body);
        assertTrue(!body.contains("jti"), "不得包含会话 jti: " + body);
    }

    @Test
    void meShouldReturn401WhenSessionDeletedOrOverwritten() throws Exception {
        User user = createTestUser("me-sess", UserStatus.ACTIVE);
        String token1 = loginAndGetToken(PREFIX + "me-sess");
        authSessionService.deleteSession(user.getId());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        // 会话被第二次登录覆盖（JTI 不匹配）
        String token2 = loginAndGetToken(PREFIX + "me-sess");
        loginAndGetToken(PREFIX + "me-sess");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturn401WithExpiredJwt() throws Exception {
        User user = createTestUser("expired", UserStatus.ACTIVE);
        Instant past = Instant.now().minusSeconds(7200);
        // 过期 token（签名有效但已过期）：Filter 解析阶段即拒绝
        String expired = ((com.workflowx.common.security.JwtServiceImpl) jwtService)
                .generateToken(user.getId(), user.getUsername(), List.of("MEMBER"), past, past.minusSeconds(60));
        authSessionService.createSession(user.getId(), "any-jti");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturn404WhenUserDeletedFromDatabase() throws Exception {
        User user = createTestUser("deleted", UserStatus.ACTIVE);
        String token = loginAndGetToken(PREFIX + "deleted");
        // 会话仍有效但用户已从库中删除 → 404，绝不返回 200 + null
        userMapper.deleteById(user.getId());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
