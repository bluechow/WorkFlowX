package com.workflowx.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.dto.UpdateUserRequest;
import com.workflowx.user.dto.UserPageQuery;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理 API 集成测试（P2-11 + P2-12）：真实 Spring Security 过滤链 + 真实 MySQL + 真实 Redis。
 * 角色账号: dev seed 的 admin(ADMIN)/user1(MEMBER)；CRUD 目标统一 p2_ctrl_test_ 前缀，用后清理。
 * 权限矩阵: 未认证 401 / MEMBER 403 / ADMIN 200·201·204；禁用即踢线全链路实测。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    private static final String PREFIX = "p2_ctrl_test_";
    private static final String PASSWORD = "CtrlPass@123";
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
    private StringRedisTemplate redisTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    private String adminToken;
    private String memberToken;

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete(SESSION_KEY_PREFIX + id);
        }
        createdUserIds.clear();
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, PREFIX));
    }

    private String token(String username, String password) throws Exception {
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

    private String admin() throws Exception {
        if (adminToken == null) {
            adminToken = token("admin", "Admin@123456");
        }
        return adminToken;
    }

    private String member() throws Exception {
        if (memberToken == null) {
            memberToken = token("user1", "Member@123456");
        }
        return memberToken;
    }

    private User createTestUser(String suffix, UserStatus status) {
        var created = userService.create(new CreateUserRequest(
                PREFIX + suffix, PREFIX + suffix + "@test.local", PASSWORD, "ctrl-" + suffix));
        createdUserIds.add(created.id());
        if (status != UserStatus.ACTIVE) {
            userService.updateStatus(-1L, created.id(), status);
        }
        return userMapper.selectById(created.id());
    }

    private String createBody(String suffix) {
        return objectMapper.valueToTree(new CreateUserRequest(
                PREFIX + suffix, PREFIX + suffix + "@test.local", PASSWORD, "ctrl-" + suffix)).toString();
    }

    // ===== 查询 =====

    @Test
    void adminListUsersShouldReturn200WithPageStructure() throws Exception {
        createTestUser("list1", UserStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/users").param("keyword", PREFIX).param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value(PREFIX + "list1"));
    }

    @Test
    void memberListUsersShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void unauthenticatedListUsersShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminGetUserShouldReturn200() throws Exception {
        User user = createTestUser("get1", UserStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/users/{id}", user.getId()).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(PREFIX + "get1"))
                .andExpect(jsonPath("$.data.email").value(PREFIX + "get1@test.local"));
    }

    @Test
    void adminGetMissingUserShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 999999999L).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ===== 创建 =====

    @Test
    void adminCreateUserShouldReturn201AndPersist() throws Exception {
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("create1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.username").value(PREFIX + "create1"));

        User persisted = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, PREFIX + "create1"));
        assertNotNull(persisted, "创建后数据库必须真实存在");
        assertTrue(persisted.getPasswordHash().startsWith("$2a$10$"), "密码必须为 BCrypt 存储");
    }

    @Test
    void createResponseMustNotContainPassword() throws Exception {
        String body = mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("nopwd")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertTrue(!body.contains("password") && !body.contains("passwordHash") && !body.contains(PASSWORD),
                "创建响应不得包含密码任何形态: " + body);
    }

    @Test
    void createDuplicateUsernameShouldReturn409() throws Exception {
        createTestUser("dupu", UserStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("dupu")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void createDuplicateEmailShouldReturn409() throws Exception {
        createTestUser("dupe", UserStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.valueToTree(new CreateUserRequest(
                                PREFIX + "dupe2", PREFIX + "dupe@test.local", PASSWORD, "n")).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void createInvalidParamsShouldReturn422() throws Exception {
        // username 2 字符 + password 无数字
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"email\":\"bad@test.local\",\"password\":\"nodigit\",\"nickname\":\"n\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void memberCreateUserShouldReturn403() throws Exception {
        // 使用合法格式的请求体: 授权失败(403)与参数校验(422)顺序上校验在前，
        // 因此本用例必须用合法 body 才能验证权限拒绝（非法 body 无论角色都是 422）
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("membercreate")))
                .andExpect(status().isForbidden());
    }

    // ===== 更新 =====

    @Test
    void adminUpdateUserShouldReturn200AndPersist() throws Exception {
        User user = createTestUser("upd", UserStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/users/{id}", user.getId()).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.valueToTree(new UpdateUserRequest(
                                PREFIX + "upd-new@test.local", "updated")).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(PREFIX + "upd-new@test.local"))
                .andExpect(jsonPath("$.data.nickname").value("updated"));

        User after = userMapper.selectById(user.getId());
        assertEquals(PREFIX + "upd-new@test.local", after.getEmail());
        assertEquals("updated", after.getNickname());
        // PUT 不允许触碰密码与状态
        assertTrue(after.getPasswordHash().startsWith("$2a$10$"));
        assertEquals(UserStatus.ACTIVE, after.getStatus());
    }

    @Test
    void memberUpdateShouldReturn403() throws Exception {
        User user = createTestUser("upd-m", UserStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/users/{id}", user.getId()).header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.valueToTree(new UpdateUserRequest(
                                PREFIX + "x@test.local", "n")).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateMissingUserShouldReturn404() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", 999999999L).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.valueToTree(new UpdateUserRequest(
                                "missing@test.local", "n")).toString()))
                .andExpect(status().isNotFound());
    }

    // ===== 状态 / 禁用即踢线 =====

    @Test
    void adminDisableUserShouldKickSessionImmediately() throws Exception {
        User target = createTestUser("kick", UserStatus.ACTIVE);
        // 目标用户先真实登录（获得 JWT + Redis 会话）
        String targetToken = token(PREFIX + "kick", PASSWORD);
        String sessionKey = SESSION_KEY_PREFIX + target.getId();
        assertNotNull(redisTemplate.opsForValue().get(sessionKey), "禁用前会话应存在");
        // 旧 token 当前可用（会话有效但无 ADMIN 角色 → 已认证 403）
        mockMvc.perform(get("/api/v1/users/{id}", target.getId()).header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/{id}/status", target.getId())
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertEquals(UserStatus.DISABLED, userMapper.selectById(target.getId()).getStatus(), "数据库必须真实变更");
        assertTrue(redisTemplate.opsForValue().get(sessionKey) == null, "禁用必须删除目标 Redis 会话");
        // 目标旧 JWT 立即失效（禁用即踢线核心断言）
        mockMvc.perform(get("/api/v1/users/{id}", target.getId()).header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminReEnableUserShouldNotAutoCreateSession() throws Exception {
        User target = createTestUser("reen", UserStatus.DISABLED);
        String sessionKey = SESSION_KEY_PREFIX + target.getId();

        mockMvc.perform(patch("/api/v1/users/{id}/status", target.getId())
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertEquals(UserStatus.ACTIVE, userMapper.selectById(target.getId()).getStatus());
        assertTrue(redisTemplate.opsForValue().get(sessionKey) == null, "启用不得自动创建会话");

        // 重新登录获得新会话
        String newToken = token(PREFIX + "reen", PASSWORD);
        assertNotNull(redisTemplate.opsForValue().get(sessionKey), "重新登录后应有新会话");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(PREFIX + "reen"));
    }

    @Test
    void memberPatchStatusShouldReturn403() throws Exception {
        User user = createTestUser("st-m", UserStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId())
                        .header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPatchOwnStatusShouldReturn400() throws Exception {
        // 规则（AI_TASKS P2-12 已记录）: 不能修改自己的状态；operatorId 取自 SecurityContext
        User adminUser = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin"));
        mockMvc.perform(patch("/api/v1/users/{id}/status", adminUser.getId())
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminPatchMissingUserShouldReturn404() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", 999999999L)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchInvalidStatusValueShouldReturn422() throws Exception {
        User user = createTestUser("st-bad", UserStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId())
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void memberCannotAccessAnyUserManagementEndpoint() throws Exception {
        User user = createTestUser("esc", UserStatus.ACTIVE);
        String m = member();
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + m))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{id}", user.getId()).header("Authorization", "Bearer " + m))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + m)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("esc1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/{id}", user.getId()).header("Authorization", "Bearer " + m)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.valueToTree(new UpdateUserRequest("e@e.local", "n")).toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId()).header("Authorization", "Bearer " + m)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden());
    }
}
