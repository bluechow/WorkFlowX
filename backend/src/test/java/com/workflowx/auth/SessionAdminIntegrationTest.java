package com.workflowx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在线会话管理（Phase A-⑤）集成测试：列表 / RBAC / 踢下线生效 / 自踢拒绝。
 * 前缀 SES- 隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionAdminIntegrationTest {

    private static final String USER_PREFIX = "sesu_";
    private static final String PASSWORD = "SesPass@123";

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

    @BeforeEach
    void purgeLeftovers() {
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
    }

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete("auth:session:" + id);
        }
        createdUserIds.clear();
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return (String) data.get("accessToken");
    }

    private Long createTargetUser() {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + "target", USER_PREFIX + "target@test.local", PASSWORD, "会话目标"));
        createdUserIds.add(created.id());
        return created.id();
    }

    @Test
    void adminSeesSessionsIncludingSelf_andCanKickTarget() throws Exception {
        Long targetId = createTargetUser();
        String adminToken = login("admin", "Admin@123456");
        String targetToken = login(USER_PREFIX + "target", PASSWORD);

        // 列表：包含 admin 自己（self 标记）与目标用户
        MvcResult result = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var list = (java.util.List<java.util.Map<String, Object>>) response.get("data");
        assertTrue(list.size() >= 2, "至少包含 admin 与目标用户");
        assertEquals(1, list.stream().filter(s -> Boolean.TRUE.equals(s.get("self"))).count());
        assertTrue(list.stream().anyMatch(s -> USER_PREFIX.equals(String.valueOf(s.get("username")))
                || String.valueOf(s.get("username")).startsWith(USER_PREFIX)));
        // 剩余有效期为正数（token 有效期内）
        Object ttl = list.stream().filter(s -> Long.parseLong(String.valueOf(s.get("userId"))) == targetId)
                .findFirst().orElseThrow().get("expiresInSeconds");
        assertTrue(Long.parseLong(String.valueOf(ttl)) > 0);

        // 踢下线 → 目标用户下一个请求 401
        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        // admin 自己不受影响
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void kickSelfIsRejected_andNonAdminGets403() throws Exception {
        createTargetUser();
        String adminToken = login("admin", "Admin@123456");
        String targetToken = login(USER_PREFIX + "target", PASSWORD);

        // MEMBER 无 user:status authority → 403
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", 1L)
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isForbidden());

        // 自踢 → 400
        Long adminId = null;
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        adminId = ((Number) ((Map<?, ?>) objectMapper.readValue(
                me.getResponse().getContentAsString(), java.util.Map.class).get("data")).get("id")).longValue();
        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能踢出自己的会话，请使用退出登录"));

        // 未认证 → 401
        mockMvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isUnauthorized());
    }
}
