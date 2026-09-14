package com.workflowx.rbac.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.rbac.dto.AssignRolePermissionsRequest;
import com.workflowx.rbac.entity.Role;
import com.workflowx.rbac.mapper.RoleMapper;
import com.workflowx.rbac.service.RoleService;
import com.workflowx.rbac.service.UserRoleService;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC REST API 集成测试（P3-03）：真实 Security 过滤链 + 真实 MySQL + 真实 Redis。
 * 权限矩阵: 未认证 401 / MEMBER（无 RBAC 权限）403 / ADMIN（全量权限）正常。
 * 数据隔离: 角色 p3_ctrl_test_ 前缀、用户 p3_ctrl_ 前缀，用后清理；系统角色/权限实测保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacControllerIntegrationTest {

    private static final String ROLE_PREFIX = "P3_CTRL_TEST_";
    private static final String USER_PREFIX = "p3_ctrl_";
    private static final String PASSWORD = "RbacCtrl@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String adminToken;
    private String memberToken;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete("auth:session:" + id);
        }
        createdUserIds.clear();
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
        roleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                .likeRight(Role::getCode, ROLE_PREFIX));
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

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", PASSWORD, "rbac-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private Long createTestRole(String suffix) {
        return roleService.create(new com.workflowx.rbac.dto.CreateRoleRequest(
                ROLE_PREFIX + suffix, "测试角色-" + suffix, null)).id();
    }

    // ===== 角色查询 =====

    @Test
    void adminListRolesShouldReturnSystemRoles() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String body = mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + admin()))
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":\"ADMIN\"") && body.contains("\"system\":true"));
    }

    @Test
    void memberListRolesShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedListRolesShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
    }

    // ===== 角色创建/更新/删除 =====

    @Test
    void adminCreateRoleShouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.rbac.dto.CreateRoleRequest(ROLE_PREFIX + "API", "接口角色", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.system").value(false));
    }

    @Test
    void adminCreateDuplicateRoleShouldReturn409() throws Exception {
        createTestRole("dup");
        mockMvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.rbac.dto.CreateRoleRequest(ROLE_PREFIX + "DUP", "重复", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCreateInvalidRoleCodeShouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.rbac.dto.CreateRoleRequest("bad-code", "非法", null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminUpdateAndDeleteNonSystemRoleShouldSucceed() throws Exception {
        Long id = createTestRole("upd");
        mockMvc.perform(put("/api/v1/roles/{id}", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.rbac.dto.UpdateRoleRequest("更新角色", "desc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新角色"));
        mockMvc.perform(delete("/api/v1/roles/{id}", id).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles/{id}", id).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDeleteSystemRoleShouldReturn400() throws Exception {
        Long adminRoleId = roleService.list().stream()
                .filter(r -> "ADMIN".equals(r.code())).findFirst().orElseThrow().id();
        mockMvc.perform(delete("/api/v1/roles/{id}", adminRoleId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ===== 角色权限绑定 =====

    @Test
    void adminGetAndReplaceRolePermissions() throws Exception {
        Long id = createTestRole("perm");
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignRolePermissionsRequest(java.util.Set.of("user:list", "role:list")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/roles/{id}/permissions", id).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("user:list"));
        // 未知权限 → 404
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignRolePermissionsRequest(java.util.Set.of("no_such:perm")))))
                .andExpect(status().isNotFound());
    }

    // ===== 权限查询 =====

    @Test
    void adminListPermissionsShouldContainSystemPermissions() throws Exception {
        String body = mockMvc.perform(get("/api/v1/permissions").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":\"user:create\"") && body.contains("\"system\":true"));
        assertTrue(!body.contains("passwordHash"), "响应不得包含敏感字段");
    }

    @Test
    void memberListPermissionsShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/permissions").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
    }

    // ===== 用户角色绑定 =====

    @Test
    void adminGetAssignRevokeAndReplaceUserRoles() throws Exception {
        Long userId = createTestUser("roles");
        // 分配
        mockMvc.perform(post("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("MEMBER"));
        // 幂等重复分配
        mockMvc.perform(post("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        // replace: MEMBER → ADMIN
        mockMvc.perform(put("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.rbac.dto.AssignUserRolesRequest(java.util.Set.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("ADMIN"));
        // 未知角色 → 404
        mockMvc.perform(post("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "NO_SUCH_ROLE"))))
                .andExpect(status().isNotFound());
        // 回收
        mockMvc.perform(delete("/api/v1/users/{id}/roles/{code}", userId, "ADMIN")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void memberCannotManageUserRoles() throws Exception {
        Long userId = createTestUser("mroles");
        mockMvc.perform(get("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/{id}/roles", userId).header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    // ===== 权限实时生效（接线核心验证） =====

    @Test
    void grantedPermissionShouldTakeEffectImmediatelyWithoutRelogin() throws Exception {
        // 1. 创建无权限用户并登录（token 中 roles 不含 ADMIN）
        Long userId = createTestUser("live");
        String userToken = token(USER_PREFIX + "live", PASSWORD);
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. 给其角色（新建角色绑定 role:list 权限）并分配给用户 —— 不重新登录
        Long roleId = createTestRole("live");
        roleService.assignPermissions(roleId, new AssignRolePermissionsRequest(java.util.Set.of("role:list")));
        userRoleService.assignRole(userId, ROLE_PREFIX + "live");

        // 3. 同一 token 立即可访问（权限实时解析，无需重签 token）
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // 4. 收权后立即 403
        userRoleService.revokeRole(userId, ROLE_PREFIX + "live");
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ===== MEMBER 在新模型下的用户管理访问（Phase 2 行为兼容） =====

    @Test
    void memberCannotListUsersUnderFineGrainedAuthorities() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsersUnderFineGrainedAuthorities() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("page", "1").param("size", "5")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
