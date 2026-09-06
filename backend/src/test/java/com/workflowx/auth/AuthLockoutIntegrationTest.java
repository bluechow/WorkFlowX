package com.workflowx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录失败限制集成测试（P2-13）：真实 MySQL + 真实 Redis。
 * 规则（ADR-010）: 15 分钟窗口 5 次失败 → 锁定；计数含不存在的 username（防枚举）；成功登录清除计数。
 * 数据隔离: 用户 p2_lock_test_ 前缀 + auth:fail:/auth:session: 键用后清理（TTL 900s 会跨运行残留，必须显式删）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthLockoutIntegrationTest {

    private static final String PREFIX = "p2_lock_test_";
    private static final String PASSWORD = "LockPass@123";
    private static final String WRONG_PASSWORD = "WrongPass@999";
    private static final String FAIL_KEY_PREFIX = "auth:fail:";
    private static final String SESSION_KEY_PREFIX = "auth:session:";
    /** 锁定测试专用的不存在用户名（统一计数语义验证） */
    private static final String GHOST_USER = "p2_lock_ghost_user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete(SESSION_KEY_PREFIX + id);
        }
        createdUserIds.clear();
        // 失败计数键 TTL 900s 会跨运行残留，必须显式清理
        redisTemplate.delete(FAIL_KEY_PREFIX + GHOST_USER);
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, PREFIX));
        redisTemplate.keys(FAIL_KEY_PREFIX + PREFIX + "*").forEach(k -> redisTemplate.delete(k));
    }

    private User createLockUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                PREFIX + suffix, PREFIX + suffix + "@test.local", PASSWORD, "lock-" + suffix));
        createdUserIds.add(created.id());
        return userMapper.selectById(created.id());
    }

    /** 使用给定密码登录，返回响应状态码 */
    private int loginStatus(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(username), objectMapper.valueToTree(password))))
                .andReturn().getResponse().getStatus();
    }

    // ===== 失败计数递增 =====

    @Test
    void failureCountShouldIncrementAtomicallyPerAttempt() throws Exception {
        User user = createLockUser("inc");
        for (int i = 1; i <= 4; i++) {
            int status = loginStatus(PREFIX + "inc", WRONG_PASSWORD);
            assertEquals(401, status, "第 " + i + " 次失败应为 401");
            assertEquals(String.valueOf(i), redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + PREFIX + "inc"),
                    "第 " + i + " 次失败后计数应为 " + i);
        }
        assertTrue(redisTemplate.getExpire(FAIL_KEY_PREFIX + PREFIX + "inc", TimeUnit.SECONDS) > 880,
                "首次失败后窗口 TTL 应接近 900s");
    }

    @Test
    void fifthFailureShouldTriggerLockWith429() throws Exception {
        User user = createLockUser("lock5");
        for (int i = 1; i <= 4; i++) {
            assertEquals(401, loginStatus(PREFIX + "lock5", WRONG_PASSWORD));
        }
        // 第 5 次失败: 响应体现已达到限制
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(PREFIX + "lock5"), objectMapper.valueToTree(WRONG_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("登录尝试次数过多，请稍后再试"));
        assertEquals("5", redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + PREFIX + "lock5"));
        // 锁定窗口重置为 15 分钟
        Long ttl = redisTemplate.getExpire(FAIL_KEY_PREFIX + PREFIX + "lock5", TimeUnit.SECONDS);
        assertTrue(ttl > 880 && ttl <= 900, "锁定后 TTL 应重置为约 900s，实际: " + ttl);
    }

    @Test
    void lockedAccountShouldRejectCorrectPasswordWithoutCreatingSession() throws Exception {
        User user = createLockUser("locked");
        for (int i = 0; i < 5; i++) {
            loginStatus(PREFIX + "locked", WRONG_PASSWORD);
        }
        // 锁定期间正确密码也拒绝，且不签发 JWT / 不创建会话
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(PREFIX + "locked"), objectMapper.valueToTree(PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(!body.contains("accessToken"), "锁定期间不得签发 token: " + body);
        assertNull(redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + user.getId()), "锁定期间不得创建会话");
        // users.status 保持原值（Redis 临时锁定 ≠ 数据库 LOCKED，ADR-010）
        assertEquals(UserStatus.ACTIVE, userMapper.selectById(user.getId()).getStatus());
    }

    @Test
    void successfulLoginShouldClearFailureCount() throws Exception {
        createLockUser("clear");
        for (int i = 0; i < 3; i++) {
            loginStatus(PREFIX + "clear", WRONG_PASSWORD);
        }
        assertEquals("3", redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + PREFIX + "clear"));
        // 成功登录 → DEL 计数
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(PREFIX + "clear"), objectMapper.valueToTree(PASSWORD))))
                .andExpect(status().isOk());
        assertNull(redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + PREFIX + "clear"), "成功登录必须清除失败计数");
        // 重新失败从 1 开始
        loginStatus(PREFIX + "clear", WRONG_PASSWORD);
        assertEquals("1", redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + PREFIX + "clear"));
    }

    @Test
    void nonexistentUsernameShouldBeCountedAndLockedWithIdenticalResponse() throws Exception {
        // 统一计数语义: 不存在的 username 同样计数与锁定，429 响应与真实用户完全一致（防枚举）
        for (int i = 0; i < 5; i++) {
            loginStatus(GHOST_USER, WRONG_PASSWORD);
        }
        String ghostResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(GHOST_USER), objectMapper.valueToTree(WRONG_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertTrue(redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + GHOST_USER) != null,
                "不存在用户名也必须计数");

        // 与真实用户锁定响应逐字节一致
        createLockUser("real");
        for (int i = 0; i < 5; i++) {
            loginStatus(PREFIX + "real", WRONG_PASSWORD);
        }
        String realResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": %s, \"password\": %s}".formatted(
                                objectMapper.valueToTree(PREFIX + "real"), objectMapper.valueToTree(WRONG_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        String normalizedGhost = ghostResponse
                .replaceAll("\"traceId\":\"[^\"]+\"", "")
                .replaceAll("\"timestamp\":\"[^\"]+\"", "");
        String normalizedReal = realResponse
                .replaceAll("\"traceId\":\"[^\"]+\"", "")
                .replaceAll("\"timestamp\":\"[^\"]+\"", "");
        assertEquals(normalizedReal, normalizedGhost, "锁定响应不得区分用户存在性");
    }

    @Test
    void concurrentFailuresMustNotBypassLimit() throws Exception {
        createLockUser("conc");
        int threads = 10;
        List<Integer> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(loginStatus(PREFIX + "conc", WRONG_PASSWORD));
                } catch (Exception ignored) {
                    statuses.add(-1);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        long four01 = statuses.stream().filter(s -> s == 401).count();
        long four29 = statuses.stream().filter(s -> s == 429).count();
        // INCR 原子性: 计数不丢失；第 5 次起返回 429，且不存在 200（密码本就是错的）
        assertEquals(threads, statuses.size());
        assertTrue(statuses.stream().noneMatch(s -> s == 200), "错误密码不得登录成功");
        assertTrue(four01 + four29 == threads, "响应只能是 401/429，实际: " + statuses);
        assertTrue(four29 >= 1, "并发下第 5 次失败必须触发锁定");
        // 最终锁定状态: 正确密码也 429
        assertEquals(429, loginStatus(PREFIX + "conc", PASSWORD));
    }
}
