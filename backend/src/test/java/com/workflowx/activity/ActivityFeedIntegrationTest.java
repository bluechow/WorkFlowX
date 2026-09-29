package com.workflowx.activity;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 活动流（V18，Final Edition FP-2）集成测试：
 * 真实业务操作（创建/分派/流转/评论）产生活动；读权限=issue:list。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityFeedIntegrationTest {

    private static final String ORG_PREFIX = "ac_org_";
    private static final String KEY_PREFIX = "AC";

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
                new CreateOrganizationRequest("AC 组织", ORG_PREFIX + suffix, null), 1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("AC 项目", KEY_PREFIX + suffix, orgId, null), 1L).id();
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
    void businessActionsProduceActivities_andReadRequiresIssueList() throws Exception {
        // 1. 创建 Issue → CREATE 活动
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(
                                "AC 活动目标", null, IssueType.TASK, IssuePriority.HIGH, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                created.getResponse().getContentAsString(), java.util.Map.class).get("data");
        Long issueId = ((Number) data.get("id")).longValue();

        // 2. 分派给自己（userId=1）→ ASSIGN 活动
        mockMvc.perform(patch("/api/v1/projects/{id}/issues/{iid}/assignee", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":1}"))
                .andExpect(status().isOk());

        // 3. 流转 OPEN→IN_PROGRESS → TRANSITION 活动
        mockMvc.perform(patch("/api/v1/projects/{id}/issues/{iid}/status", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.workflowx.issue.dto.TransitionIssueStatusRequest(
                                        com.workflowx.issue.entity.IssueStatus.OPEN,
                                        com.workflowx.issue.entity.IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());

        // 4. 评论 → COMMENT 活动
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/comments", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"AC 活动评论\"}"))
                .andExpect(status().isCreated());

        // 5. 读活动：ADMIN（issue:list）→ 4 条，按时间倒序，含 issueNo 与摘要
        MvcResult activities = mockMvc.perform(get("/api/v1/projects/{id}/activities", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        var list = (java.util.List<?>) objectMapper.readValue(
                activities.getResponse().getContentAsString(), java.util.Map.class).get("data");
        assertEquals(4, list.size());
        var first = (java.util.Map<?, ?>) list.get(0);
        assertEquals("COMMENT", first.get("action"));
        assertEquals(1, ((Number) first.get("issueNo")).intValue());
        var create = (java.util.Map<?, ?>) list.get(3);
        assertEquals("CREATE", create.get("action"));
        // 摘要含「创建了」与项目 key 前缀的业务编号（宽松断言，不绑死 key 后缀）
        String summary = String.valueOf(create.get("summary"));
        org.junit.jupiter.api.Assertions.assertTrue(summary.startsWith("创建了 ") && summary.contains("-1 "),
                "CREATE 摘要应含业务编号: " + summary);

        // MEMBER（无 issue:list）→ 403；未认证 → 401
        mockMvc.perform(get("/api/v1/projects/{id}/activities", projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{id}/activities", projectId))
                .andExpect(status().isUnauthorized());
    }
}
