package com.workflowx.issue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.service.ProjectService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue REST API 集成测试（P6-03）：真实 Security 链 + 真实 MySQL + 真实 Redis。
 * 权限双层: hasAuthority（issue:*）+ Service 数据级项目成员校验。
 * 数据隔离: 项目 P6CTRL 前缀 key、组织 ORG_P6CTRL_*、用户 p6ctrl_ 前缀，用后清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class IssueControllerIntegrationTest {

    private static final String KEY_PREFIX = "P6CTRL";
    private static final String ORG_PREFIX = "ORG_P6CTRL_";
    private static final String USER_PREFIX = "p6ctrl_";
    private static final String PASSWORD = "IssCtrl@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private IssueMapper issueMapper;

    @Autowired
    private ProjectMemberService projectMemberService;

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
        issueMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Issue>()
                .likeRight(Issue::getTitle, "CTRL-"));
        projectMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>()
                .likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Organization>()
                .likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
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
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", PASSWORD, "iss-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private Long createTestOrg(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orgs").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(
                                "Issue 组织-" + suffix, (ORG_PREFIX + suffix).toUpperCase(), null))))
                .andExpect(status().isCreated())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Long createTestProject(String suffix, Long orgId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(
                                "Issue 项目-" + suffix, (KEY_PREFIX + suffix).toUpperCase(), orgId, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private String createBody(String title) throws Exception {
        return objectMapper.writeValueAsString(new CreateIssueRequest(
                title, "CTRL- 描述", com.workflowx.issue.entity.IssueType.BUG,
                com.workflowx.issue.entity.IssuePriority.HIGH,
                com.workflowx.issue.entity.IssueSeverity.S2, null));
    }

    // ===== 权限矩阵 =====

    @Test
    void rbacPermissionMatrixForIssues() throws Exception {
        Long orgId = createTestOrg("MTX");
        Long projectId = createTestProject("MTX", orgId);
        // ADMIN 有 authority 且为项目成员（OWNER）→ 200
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
        // MEMBER user1 无 issue 权限 → 403
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
        // 未认证 → 401
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId)).andExpect(status().isUnauthorized());
    }

    // ===== 创建 =====

    @Test
    void adminCreateIssueShouldReturn201WithAutoReporterAndIssueNo() throws Exception {
        Long orgId = createTestOrg("C201");
        Long projectId = createTestProject("C201", orgId);
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("CTRL-第一个")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.issueNo").value(1))
                .andExpect(jsonPath("$.data.reporterId").value(1))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // 客户端伪造 reporter_id 无效（DTO 无该字段，忽略）
        String bodyWithFakeReporter = """
                {"title":"CTRL-伪造reporter","description":null,"type":"BUG",
                 "priority":"HIGH","severity":"S1","assigneeId":null,"reporterId":999999}
                """;
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithFakeReporter))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reporterId").value(1));

        // issue_no 递增
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void createValidationErrors() throws Exception {
        Long orgId = createTestOrg("V422");
        Long projectId = createTestProject("V422", orgId);
        // title 空
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":null,\"type\":\"BUG\",\"priority\":\"HIGH\",\"severity\":\"S1\"}"))
                .andExpect(status().isUnprocessableEntity());
        // 非法枚举
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"description\":null,\"type\":\"NOT_A_TYPE\",\"priority\":\"HIGH\",\"severity\":null}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createAssigneeRules() throws Exception {
        Long orgId = createTestOrg("ASG");
        Long projectId = createTestProject("ASG", orgId);
        Long orgMemberOnly = createTestUser("orgonly");
        organizationService.addMember(orgId,
                new AddOrganizationMemberRequest(orgMemberOnly, "MEMBER", null));
        Long projectMember = createTestUser("projmem");
        // 非项目成员（组织成员）→ 400
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(
                                "CTRL-分派", null, com.workflowx.issue.entity.IssueType.TASK,
                                com.workflowx.issue.entity.IssuePriority.MEDIUM, null, orgMemberOnly))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 成为项目成员 → 201
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(orgMemberOnly, "MEMBER"), 1L);
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(
                                "CTRL-分派OK", null, com.workflowx.issue.entity.IssueType.TASK,
                                com.workflowx.issue.entity.IssuePriority.MEDIUM, null, orgMemberOnly))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assigneeId").value(orgMemberOnly.intValue()));
    }

    // ===== 更新/状态/分派 =====

    @Test
    void updateAndStatusAndAssignFlow() throws Exception {
        Long orgId = createTestOrg("FLOW");
        Long projectId = createTestProject("FLOW", orgId);
        Long member = createTestUser("flowm");
        organizationService.addMember(orgId,
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(member, "MEMBER"), 1L);
        String memberToken = token(USER_PREFIX + "flowm", PASSWORD);

        // member 创建（issue:create authority 需授予——member 无权限，先经 RBAC 授予）
        // member 无 issue:create → 403（权限层）
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("CTRL-越权")))
                .andExpect(status().isForbidden());

        // admin 创建
        MvcResult issueResult = mockMvc
                .perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("CTRL-流程")))
                .andExpect(status().isCreated())
                .andReturn();
        var issueBody = objectMapper.readValue(issueResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var issueData = (java.util.Map<String, Object>) issueBody.get("data");
        Long issueId = ((Number) issueData.get("id")).longValue();

        // 更新（title/priority），验证 issueNo/reporter 不变
        var before = objectMapper.readValue(mockMvc
                .perform(get("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var beforeData = (java.util.Map<String, Object>) before.get("data");
        MvcResult putResult = mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.workflowx.issue.dto.UpdateIssueRequest(
                                "CTRL-流程-改", null, com.workflowx.issue.entity.IssuePriority.URGENT, null, null))))
                .andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "p7_debug.txt"),
                "PUT_STATUS=" + putResult.getResponse().getStatus() + " PUT_BODY="
                        + putResult.getResponse().getContentAsString());
        mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.workflowx.issue.dto.UpdateIssueRequest(
                                "CTRL-流程-改", null, com.workflowx.issue.entity.IssuePriority.URGENT, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("CTRL-流程-改"))
                .andExpect(jsonPath("$.data.issueNo").value(((Number) beforeData.get("issueNo")).longValue()))
                .andExpect(jsonPath("$.data.reporterId").value(((Number) beforeData.get("reporterId")).longValue()));

        // 状态 PATCH（ADR-017: fromStatus 并发保护 + 正式矩阵校验；OPEN→IN_PROGRESS 合法）
        mockMvc.perform(patch("/api/v1/projects/{id}/issues/{iid}/status", projectId, issueId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatus\":\"OPEN\",\"toStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 分派给项目成员（issue:assign）
        mockMvc.perform(patch("/api/v1/projects/{id}/issues/{iid}/assignee", projectId, issueId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("assigneeId", member.intValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(member.intValue()));

        // 取消分派（assigneeId=null）→ 200 且写库为空（回归: MP updateById 忽略 null 的坑）
        mockMvc.perform(patch("/api/v1/projects/{id}/issues/{iid}/assignee", projectId, issueId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").doesNotExist());

        // 404: 跨项目访问 A 项目 issue 用 B 项目路径
        Long orgB = createTestOrg("FLOWB");
        Long projectB = createTestProject("FLOWB", orgB);
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}", projectB, issueId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());

        // traceId 存在
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void nonProjectMemberWithAuthorityShouldReturn403OnWrite() throws Exception {
        // member 无 issue 权限；先授予 issue:update 再验证数据级（非项目成员）
        Long orgId = createTestOrg("DATAlvl");
        Long projectId = createTestProject("DATAlvl", orgId);
        MvcResult issueResult = mockMvc
                .perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("CTRL-数据级")))
                .andExpect(status().isCreated())
                .andReturn();
        var issueBody = objectMapper.readValue(issueResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var issueData = (java.util.Map<String, Object>) issueBody.get("data");
        Long issueId = ((Number) issueData.get("id")).longValue();

        // 经 RBAC 授予 user1 issue:update
        MvcResult roleResult = mockMvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"P6_CTRL_ISSUE_UPD\",\"name\":\"issue-upd\",\"description\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        var roleBody = objectMapper.readValue(roleResult.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var roleData = (java.util.Map<String, Object>) roleBody.get("data");
        Long roleId = ((Number) roleData.get("id")).longValue();
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", roleId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"issue:update\",\"issue:assign\"]}"))
                .andExpect(status().isOk());
        Long user1Id = 2L;
        mockMvc.perform(post("/api/v1/users/{id}/roles", user1Id).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"P6_CTRL_ISSUE_UPD\"}"))
                .andExpect(status().isOk());

        // user1 有 issue:update authority 但非项目成员 → 403（数据级）
        mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.workflowx.issue.dto.UpdateIssueRequest(
                                "越权更新", null, null, null, null))))
                .andExpect(status().isForbidden());

        // 清理: 移除角色与绑定
        mockMvc.perform(delete("/api/v1/roles/{id}", roleId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk());
    }

    // ===== 看板（Phase A-②）=====

    @Test
    void boardReturnsAllIssuesOfProject() throws Exception {
        Long orgId = createTestOrg("BRD");
        Long projectId = createTestProject("BRD", orgId);
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("CTRL- 看板甲")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId).header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("CTRL- 看板乙")))
                .andExpect(status().isCreated());

        // ADMIN（issue:list authority）→ 200，返回项目内全部 Issue（非分页）
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{id}/issues/board", projectId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var list = (java.util.List<java.util.Map<String, Object>>) response.get("data");
        assertEquals(2, list.size());

        // MEMBER user1 无 issue:list authority → 403；未认证 → 401；项目不存在 → 404
        mockMvc.perform(get("/api/v1/projects/{id}/issues/board", projectId)
                        .header("Authorization", "Bearer " + member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{id}/issues/board", projectId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{id}/issues/board", 999999999L)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void issueEndpointsShouldReturn404ForCrossProjectAndMissing() throws Exception {
        Long orgId = createTestOrg("N404");
        Long projectId = createTestProject("N404", orgId);
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}", projectId, 999999999L)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }
}
