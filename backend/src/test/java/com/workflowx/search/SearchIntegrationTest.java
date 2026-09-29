package com.workflowx.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局搜索（Final Edition FP-6）集成测试：命中/权限收敛/空关键词。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchIntegrationTest {

    private static final String ORG_PREFIX = "se_org_";
    private static final String KEY_PREFIX = "SE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private com.workflowx.org.mapper.OrganizationMapper orgMapper;

    private Long projectId;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin@123456");
        memberToken = login("user1", "Member@123456");
        String suffix = String.valueOf(System.currentTimeMillis() % 100000000);
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("搜索组织", ORG_PREFIX + suffix, null), 1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("支付网关重构" + suffix, KEY_PREFIX + suffix, orgId, null), 1L).id();
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(
                                "网关超时重试策略", null, IssueType.TASK, IssuePriority.HIGH,
                                null, null, null, null))))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() {
        orgMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .likeRight(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        return (String) ((java.util.Map<?, ?>) response.get("data")).get("accessToken");
    }

    @Test
    void searchHitsAndPermissionScoping() throws Exception {
        // ADMIN：项目命中（名称关键词）
        MvcResult r = mockMvc.perform(get("/api/v1/search").param("keyword", "支付网关重构")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                r.getResponse().getContentAsString(), java.util.Map.class).get("data");
        var projects = (java.util.List<?>) data.get("projects");
        assertTrue(projects.size() >= 1, "项目应命中");

        mockMvc.perform(get("/api/v1/search").param("keyword", "网关超时重试")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.issues.length()").value(1))
                .andExpect(jsonPath("$.data.issues[0].projectKey").exists())
                .andExpect(jsonPath("$.data.issues[0].status").value("OPEN"));

        // 演示数据含 demo_admin 等用户名亦命中 "admin"——断言包含且数量≥1（不绑死环境）
        mockMvc.perform(get("/api/v1/search").param("keyword", "admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.users.length()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.users[?(@.username == 'admin')]").exists());

        // MEMBER（无三类权限）：结果全空（不泄露任何存在性）
        mockMvc.perform(get("/api/v1/search").param("keyword", "支付网关重构")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(jsonPath("$.data.projects.length()").value(0))
                .andExpect(jsonPath("$.data.issues.length()").value(0))
                .andExpect(jsonPath("$.data.users.length()").value(0));

        // 空关键词 → 空结果；未认证 → 401
        mockMvc.perform(get("/api/v1/search").param("keyword", " ")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.projects.length()").value(0));
        mockMvc.perform(get("/api/v1/search").param("keyword", "x"))
                .andExpect(status().isUnauthorized());
    }
}
