package com.workflowx.org.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.common.web.PageVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateDepartmentRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组织架构 REST API 集成测试（P4-02）：真实 Security 链 + 真实 MySQL + 真实 Redis。
 * 权限矩阵: 未认证 401 / MEMBER（无 org 权限）403 / ADMIN（V5 全量权限）正常。
 * 数据隔离: 组织 ORG_P4_CTRL_* 前缀、用户 p4_ctrl_ 前缀，用后清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrgControllerIntegrationTest {

    private static final String ORG_PREFIX = "ORG_P4_CTRL_";
    private static final String USER_PREFIX = "p4_ctrl_";
    private static final String PASSWORD = "OrgCtrl@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private com.workflowx.rbac.mapper.RoleMapper roleMapper;

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
        organizationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Organization>()
                .likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
        roleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.rbac.entity.Role>()
                .likeRight(com.workflowx.rbac.entity.Role::getCode, "P4_CTRL_"));
        redisTemplate.delete("auth:session:1");
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
        var created = userService.create(new com.workflowx.user.dto.CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", PASSWORD, "ctrl-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    /** 经 API 创建组织（operator=admin，创建者即 OWNER） */
    private Long createTestOrg(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "控制测试组织-" + suffix, ORG_PREFIX + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    // ===== 权限矩阵 =====

    @Test
    void rbacPermissionMatrixForOrgEndpoints() throws Exception {
        createTestOrg("MATRIX");
        // ADMIN 正常
        mockMvc.perform(get("/api/v1/orgs").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        // MEMBER 无 org 权限
        mockMvc.perform(get("/api/v1/orgs").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
        // 未认证
        mockMvc.perform(get("/api/v1/orgs")).andExpect(status().isUnauthorized());
    }

    // ===== 组织创建（OWNER 自动产生） =====

    @Test
    void adminCreateOrgShouldReturn201WithOwnerMembership() throws Exception {
        Long orgId = createTestOrg("OWNED");
        // admin(userId=1) 创建即 OWNER 成员
        mockMvc.perform(get("/api/v1/orgs/{id}/members", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].userId").value(1));
    }

    @Test
    void createDuplicateOrgCodeShouldReturn409() throws Exception {
        createTestOrg("DUPC");
        mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "重复组织", ORG_PREFIX + "DUPC", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvalidOrgCodeShouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "非法组织", "bad-code", null))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ===== 组织更新/删除（OWNER 数据级规则） =====

    @Test
    void adminUpdateOrgShouldReturn200() throws Exception {
        Long orgId = createTestOrg("UPD");
        mockMvc.perform(put("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.org.dto.UpdateOrganizationRequest("更新组织", "d"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新组织"));
    }

    @Test
    void orgDeleteRequiresOwnerEvenWithOrgDeleteAuthority() throws Exception {
        // 经 RBAC API 组合: 建角色+绑 org:create/org:delete 授予用户（有 authority 但非 ADMIN）
        Long creatorId = createTestUser("creator");
        MvcResult roleResult = mockMvc
                .perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"P4_CTRL_CREATOR\",\"name\":\"组织创建者\",\"description\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        var roleBody = objectMapper.readValue(roleResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var roleData = (java.util.Map<String, Object>) roleBody.get("data");
        Long roleId = ((Number) roleData.get("id")).longValue();
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", roleId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"org:create\",\"org:delete\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/{id}/roles", creatorId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"P4_CTRL_CREATOR\"}"))
                .andExpect(status().isOk());

        // 该用户有 authority 且创建组织（成为 OWNER）
        String ownerToken = token(USER_PREFIX + "creator", PASSWORD);
        Long orgId;
        {
            MvcResult result = mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                    "他人组织", ORG_PREFIX + "OTHEROWN", null))))
                    .andExpect(status().isCreated())
                    .andReturn();
            var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
            @SuppressWarnings("unchecked")
            var data = (java.util.Map<String, Object>) response.get("data");
            orgId = ((Number) data.get("id")).longValue();
        }

        // member1 无 org:delete authority → 403（权限层）
        mockMvc.perform(delete("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());

        // ADMIN 有 authority 但不是 OWNER → 403（数据级规则）
        mockMvc.perform(delete("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // OWNER 本人删除成功
        mockMvc.perform(delete("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanDeleteOwnOrg() throws Exception {
        // admin 创建（OWNER=admin）→ admin 删除成功
        Long orgId = createTestOrg("OWNDEL");
        mockMvc.perform(delete("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orgs/{id}", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    // ===== 成员管理 =====

    @Test
    void adminAddListAndRemoveMember() throws Exception {
        Long orgId = createTestOrg("MEMBERS");
        Long userId = createTestUser("member1");

        mockMvc.perform(post("/api/v1/orgs/{id}/members", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddOrganizationMemberRequest(userId, "MEMBER", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        // 重复添加 409
        mockMvc.perform(post("/api/v1/orgs/{id}/members", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddOrganizationMemberRequest(userId, "MEMBER", null))))
                .andExpect(status().isConflict());

        // 移除 OWNER 400
        mockMvc.perform(delete("/api/v1/orgs/{id}/members/{uid}", orgId, 1)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest());

        // 移除普通成员
        mockMvc.perform(delete("/api/v1/orgs/{id}/members/{uid}", orgId, userId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());

        // member 无成员管理权限
        mockMvc.perform(post("/api/v1/orgs/{id}/members", orgId).header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddOrganizationMemberRequest(userId, "MEMBER", null))))
                .andExpect(status().isForbidden());
    }

    // ===== 部门 =====

    @Test
    void adminDepartmentCrudWithCycleProtection() throws Exception {
        Long orgId = createTestOrg("DEPTS");
        // 创建根部门
        MvcResult parentResult = mockMvc
                .perform(post("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDepartmentRequest("研发部", "RD", null, null))))
                .andExpect(status().isOk())
                .andReturn();
        var parentBody = objectMapper.readValue(parentResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var parentData = (java.util.Map<String, Object>) parentBody.get("data");
        Long parent = ((Number) parentData.get("id")).longValue();
        assertTrue(parent > 0);

        // 创建子部门
        mockMvc.perform(post("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDepartmentRequest("后端组", "BE", null, parent))))
                .andExpect(status().isOk());

        // 部门列表
        mockMvc.perform(get("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 防环: 父挂子 → 400
        mockMvc.perform(patch("/api/v1/departments/{id}", parent).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", "研发部", "parentId", parent))))
                .andExpect(status().isBadRequest());

        // 同组织 code 重复 → 409
        mockMvc.perform(post("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDepartmentRequest("重名部门", "RD", null, null))))
                .andExpect(status().isConflict());

        // 删除父部门 → 子级提升为根（parent 已删 → 404）
        mockMvc.perform(delete("/api/v1/departments/{id}", parent).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/departments/{id}", parent).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void memberCannotManageDepartments() throws Exception {
        Long orgId = createTestOrg("DEPTM");
        mockMvc.perform(post("/api/v1/orgs/{id}/departments", orgId).header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDepartmentRequest("部门", "DEPT", null, null))))
                .andExpect(status().isForbidden());
    }

    // ===== 404 =====

    @Test
    void orgEndpointsShouldReturn404ForMissingResources() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/999999999").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/departments/999999999").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void orgPageResponseShouldBePageVo() throws Exception {
        createTestOrg("PAGE");
        String body = mockMvc.perform(get("/api/v1/orgs").param("keyword", ORG_PREFIX)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var root = objectMapper.readTree(body);
        assertEquals(200, root.get("code").asInt());
        var pageNode = root.get("data");
        assertTrue(pageNode.get("total").asLong() >= 1);
        var firstCode = pageNode.get("list").get(0).get("code").asText();
        assertTrue(firstCode.startsWith(ORG_PREFIX), "keyword 过滤后 code 应匹配前缀");
    }
}
