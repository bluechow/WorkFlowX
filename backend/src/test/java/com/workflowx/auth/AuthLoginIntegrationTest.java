package com.workflowx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.security.JwtService;
import com.workflowx.user.dto.CreateUserRequest;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录全链路集成测试（P2-07 + P2-08）：真实 MySQL + 真实 Redis（compose 基础设施）。
 * 覆盖: 成功登录响应 / Redis 会话写入与 TTL / 单会话覆盖 / last_login_at / 统一错误 / 状态拒绝 / 校验失败。
 * 数据隔离: 用户统一 p2_auth_test_ 前缀，用后清理用户（user_roles 级联删除）与 Redis 会话键，不污染 admin/user1。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginIntegrationTest {

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

    /** 创建测试用户（可选授予 MEMBER 角色），记录 id 供清理 */
    private User createTestUser(String suffix, UserStatus status, boolean withRole) {
        var created = userService.create(new CreateUserRequest(
                PREFIX + suffix, PREFIX + suffix + "@test.local", PASSWORD, "auth-" + suffix));
        createdUserIds.add(created.id());
        if (withRole) {
            jdbcTemplate.update("""
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'MEMBER'
                    WHERE u.username = ?
                    """, PREFIX + suffix);
        }
        if (status != UserStatus.ACTIVE) {
            userService.updateStatus(created.id(), status);
        }
        return userMapper.selectById(created.id());
    }

    private String loginBody(String username, String password) {
        return """
                {"username": %s, "password": %s}
                """.formatted(objectMapper.valueToTree(username), objectMapper.valueToTree(password));
    }

    /** 登录并返回 accessToken；断言 HTTP 200 */
    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return (String) data.get("accessToken");
    }

    // ===== 成功路径 =====

    @Test
    void loginSuccessShouldReturnCompleteResponse() throws Exception {
        createTestUser("ok", UserStatus.ACTIVE, true);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PREFIX + "ok", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.username").value(PREFIX + "ok"))
                .andExpect(jsonPath("$.data.roles[0]").value("MEMBER"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(!body.contains("password") && !body.contains("password_hash"),
                "登录响应不得包含密码字段: " + body);
    }

    @Test
    void loginTokenShouldBeParseableWithCorrectClaims() throws Exception {
        createTestUser("claims", UserStatus.ACTIVE, true);
        String token = loginAndGetToken(PREFIX + "claims", PASSWORD);
        JwtPayload payload = jwtService.parseToken(token);
        assertEquals(PREFIX + "claims", payload.username());
        assertEquals(List.of("MEMBER"), payload.roles());
    }

    @Test
    void loginShouldWriteRedisSessionWithMatchingJtiAndTtl() throws Exception {
        User user = createTestUser("session", UserStatus.ACTIVE, false);
        String token = loginAndGetToken(PREFIX + "session", PASSWORD);
        String jti = jwtService.parseToken(token).jti();

        String sessionKey = SESSION_KEY_PREFIX + user.getId();
        assertEquals(jti, redisTemplate.opsForValue().get(sessionKey), "Redis 会话 jti 必须与 token 一致");
        Long ttl = redisTemplate.getExpire(sessionKey, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 7190 && ttl <= 7200, "TTL 应接近 2 小时，实际: " + ttl);
    }

    @Test
    void loginShouldUpdateLastLoginAt() throws Exception {
        User user = createTestUser("lastlogin", UserStatus.ACTIVE, false);
        assertNull(user.getLastLoginAt(), "登录前 last_login_at 应为空");
        loginAndGetToken(PREFIX + "lastlogin", PASSWORD);
        User after = userMapper.selectById(user.getId());
        assertNotNull(after.getLastLoginAt(), "登录成功后 last_login_at 必须更新");
    }

    // ===== 单会话覆盖 =====

    @Test
    void secondLoginShouldOverwriteSessionAndInvalidateFirstToken() throws Exception {
        User user = createTestUser("over", UserStatus.ACTIVE, false);
        String token1 = loginAndGetToken(PREFIX + "over", PASSWORD);
        String token2 = loginAndGetToken(PREFIX + "over", PASSWORD);

        assertNotEquals(token1, token2);
        String jti1 = jwtService.parseToken(token1).jti();
        String jti2 = jwtService.parseToken(token2).jti();
        assertNotEquals(jti1, jti2, "两次登录 jti 必须不同");

        String sessionKey = SESSION_KEY_PREFIX + user.getId();
        assertEquals(jti2, redisTemplate.opsForValue().get(sessionKey), "后登录必须覆盖 Redis 会话");

        // 第一次 token：签名有效但会话已被覆盖 → 401
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());
        // 第二次 token：有效 → 通过认证（无 Controller → 404）
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedSessionShouldInvalidateToken() throws Exception {
        User user = createTestUser("del", UserStatus.ACTIVE, false);
        String token = loginAndGetToken(PREFIX + "del", PASSWORD);
        authSessionService.deleteSession(user.getId());
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ===== 失败路径 =====

    @Test
    void nonexistentUsernameShouldReturnUnified401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no_such_user_p2", "Whatever@123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void wrongPasswordShouldReturnUnified401SameMessage() throws Exception {
        createTestUser("wrongpwd", UserStatus.ACTIVE, false);
        String wrongPasswordResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PREFIX + "wrongpwd", "WrongPass@999")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        // 与"用户不存在"消息完全一致（防用户枚举）
        String notFoundResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no_such_user_p2", "Whatever@123")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertTrue(wrongPasswordResponse.contains("用户名或密码错误"));
        assertTrue(notFoundResponse.contains("用户名或密码错误"));
    }

    @Test
    void disabledUserShouldReturn403() throws Exception {
        createTestUser("disabled", UserStatus.DISABLED, false);
        // 密码正确但账号禁用 → 403（状态检查在密码验证之后，不泄漏用户存在性）
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PREFIX + "disabled", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void lockedUserShouldReturn403() throws Exception {
        createTestUser("locked", UserStatus.LOCKED, false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PREFIX + "locked", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void blankUsernameOrPasswordShouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"someone\", \"password\": \"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginMustNotPrintTokenOrPasswordToLogs() throws Exception {
        createTestUser("nolog", UserStatus.ACTIVE, true);
        String token = loginAndGetToken(PREFIX + "nolog", PASSWORD);
        String tokenPayload = token.split("\\.")[1];
        // 日志文件中不得出现完整 token 或其 payload 段
        String logContent = java.nio.file.Files.readString(
                java.nio.file.Path.of("logs", "workflowx-backend.log"));
        assertTrue(!logContent.contains(token), "日志不得打印完整 JWT");
        assertTrue(!logContent.contains(tokenPayload), "日志不得打印 JWT payload");
        assertTrue(!logContent.contains(PASSWORD), "日志不得打印明文密码");
    }

    // ===== 回归 =====

    @Test
    void healthShouldRemainPublicAndProtectedStillRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    // ===== JWT 无敏感信息 =====

    @Test
    void loginJwtMustNotContainSensitiveClaims() throws Exception {
        createTestUser("nosensitive", UserStatus.ACTIVE, false);
        String token = loginAndGetToken(PREFIX + "nosensitive", PASSWORD);
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(!payloadJson.toLowerCase().contains("password"));
        assertTrue(!payloadJson.toLowerCase().contains("email"));
    }
}
