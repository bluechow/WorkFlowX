package com.workflowx.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目 REST API 集成测试（P5-02）：真实 Security 链 + 真实 MySQL + 真实 Redis。
 * 权限矩阵: 未认证 401 / MEMBER（无 project 权限）403 / ADMIN 正常；
 * 数据级归属: 非组织成员即使有 project:update authority 也 403（ADR-014）。
 * 数据隔离: 项目 P5_CTRL 前缀 key、组织 ORG_P5_CTRL_*、用户 p5_ctrl_ 前缀，用后清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerIntegrationTest {

    private static final String KEY_PREFIX = "P5CTRL";
    private static final String ORG_PREFIX = "ORG_P5_CTRL_";
    private static final String USER_PREFIX = "p5_ctrl_";
    private static final String PASSWORD = "ProjCtrl@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProjectMapper projectMapper;

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
        projectMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>()
                .likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Organization>()
                .likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
        roleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.rbac.entity.Role>()
                .likeRight(com.workflowx.rbac.entity.Role::getCode, "P5_CTRL_"));
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
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", PASSWORD, "proj-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    /** admin 创建组织（admin 即 OWNER 成员），返回 orgId */
    private Long createTestOrg(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "项目组织-" + suffix, ORG_PREFIX + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Long createProject(String keySuffix, Long orgId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "项目-" + keySuffix, KEY_PREFIX + keySuffix, orgId, "d"))))
                .andExpect(status().isCreated())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    // ===== 权限矩阵 =====

    @Test
    void rbacPermissionMatrixForProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    // ===== 创建 =====

    @Test
    void adminCreateProjectShouldReturn201() throws Exception {
        Long orgId = createTestOrg("CREATE");
        mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "控制项目", KEY_PREFIX + "CREATE", orgId, "d"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value(KEY_PREFIX + "CREATE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ownerId").value(1));
    }

    @Test
    void createDuplicateKeyShouldReturn409() throws Exception {
        Long orgId = createTestOrg("DUP");
        createProject("DUP", orgId);
        mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "重复项目", KEY_PREFIX + "DUP", orgId, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvalidKeyShouldReturn422() throws Exception {
        Long orgId = createTestOrg("INV");
        mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "非法", "bad-key", orgId, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createWithMissingOrgShouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "无组织", KEY_PREFIX + "NOORG", 999999999L, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createByNonOrgMemberShouldReturn403() throws Exception {
        Long orgId = createTestOrg("NOM");
        Long outsider = createTestUser("outsider");
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(outsider, "MEMBER", null));
        // outsider 是组织成员但无 project:create authority → 403（权限层）
        String outsiderToken = token(USER_PREFIX + "outsider", PASSWORD);
        mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "x", KEY_PREFIX + "NOM", orgId, null))))
                .andExpect(status().isForbidden());
    }

    // ===== 查询 =====

    @Test
    void adminPageProjectsWithKeywordAndStatusFilter() throws Exception {
        Long orgId = createTestOrg("PAGE");
        createProject("PAGEA", orgId);
        Long archived = createProject("PAGEB", orgId);
        mockMvc.perform(patch("/api/v1/projects/{id}/status", archived)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/projects").param("keyword", KEY_PREFIX + "PAGE")
                        .param("status", "ACTIVE").param("orgId", String.valueOf(orgId))
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].key").value(KEY_PREFIX + "PAGEA"));

        mockMvc.perform(get("/api/v1/projects").param("keyword", KEY_PREFIX + "PAGE")
                        .param("orgId", String.valueOf(orgId))
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void getMissingProjectShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/999999999").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    // ===== 更新/归档 =====

    @Test
    void adminUpdateAndArchiveProject() throws Exception {
        Long orgId = createTestOrg("UPDA");
        Long id = createProject("UPDA", orgId);

        mockMvc.perform(put("/api/v1/projects/{id}", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.project.dto.UpdateProjectRequest("改名项目", "new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名项目"))
                .andExpect(jsonPath("$.data.key").value(KEY_PREFIX + "UPDA"));

        // 归档
        mockMvc.perform(patch("/api/v1/projects/{id}/status", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // 恢复
        mockMvc.perform(patch("/api/v1/projects/{id}/status", id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ===== 数据级归属（ADR-014 核心） =====

    @Test
    void authorityWithoutOrgMembershipShouldReturn403() throws Exception {
        // 组织由其他用户创建（owner=该用户）；admin 有全部 authority 但非成员
        Long ownerId = createTestUser("otherown");
        String ownerToken = token(USER_PREFIX + "otherown", PASSWORD);

        // 给 owner 授予 org:create + project:create/update 权限（经 RBAC API）
        MvcResult roleResult = mockMvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"P5_CTRL_CREATOR\",\"name\":\"项目创建者\",\"description\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        var roleBody = objectMapper.readValue(roleResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var roleData = (java.util.Map<String, Object>) roleBody.get("data");
        Long roleId = ((Number) roleData.get("id")).longValue();
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", roleId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"org:create\",\"org:delete\",\"org:assign_member\",\"org:get\",\"project:create\",\"project:update\",\"project:list\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/{id}/roles", ownerId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"P5_CTRL_CREATOR\"}"))
                .andExpect(status().isOk());

        // owner 创建组织与项目
        MvcResult orgResult = mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "他组织项目", ORG_PREFIX + "OTHER", null))))
                .andExpect(status().isCreated())
                .andReturn();
        var orgResponse = objectMapper.readValue(orgResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var orgData = (java.util.Map<String, Object>) orgResponse.get("data");
        Long orgId = ((Number) orgData.get("id")).longValue();

        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "他组织项目", KEY_PREFIX + "OTHER", orgId, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var projectResponse = objectMapper.readValue(projectResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var projectData = (java.util.Map<String, Object>) projectResponse.get("data");
        Long projectId = ((Number) projectData.get("id")).longValue();

        // admin 有 project:update authority 但非该组织成员 → 403（数据级）
        mockMvc.perform(put("/api/v1/projects/{id}", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.workflowx.project.dto.UpdateProjectRequest("越权改名", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 将 admin 加入组织成员后 → 200（归属建立即放行）
        mockMvc.perform(post("/api/v1/orgs/{id}/members", orgId).header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddOrganizationMemberRequest(1L, "ADMIN", null))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/projects/{id}", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.workflowx.project.dto.UpdateProjectRequest("加入后改名", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("加入后改名"));

        // 清理角色
        mockMvc.perform(delete("/api/v1/roles/{id}", roleId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
    }
}
