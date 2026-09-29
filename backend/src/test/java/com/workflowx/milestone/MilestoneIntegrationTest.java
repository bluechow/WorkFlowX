package com.workflowx.milestone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.milestone.dto.MilestoneRequests;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 里程碑（V19，Final Edition FP-4）集成测试：CRUD/唯一性/进度统计/
 * 工作项归属（设置/清除/跨项目 404）/删除退回未归属/权限矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MilestoneIntegrationTest {

    private static final String ORG_PREFIX = "ms_org_";
    private static final String KEY_PREFIX = "MS";

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
    private Long otherProjectId;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin@123456");
        memberToken = login("user1", "Member@123456");
        String suffix = String.valueOf(System.currentTimeMillis() % 100000000);
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("MS 组织", ORG_PREFIX + suffix, null), 1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("MS 项目甲", KEY_PREFIX + "A" + suffix, orgId, null), 1L).id();
        otherProjectId = projectService.create(
                new CreateProjectRequest("MS 项目乙", KEY_PREFIX + "B" + suffix, orgId, null), 1L).id();
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

    private Long createMilestone(String token, Long pid, String name, String dueDate) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        if (dueDate != null) {
            body.put("dueDate", dueDate);
        }
        MvcResult r = mockMvc.perform(post("/api/v1/projects/{id}/milestones", pid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                r.getResponse().getContentAsString(), java.util.Map.class).get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Long createIssue(String token, Long pid, String title) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", title);
        body.put("type", "TASK");
        MvcResult r = mockMvc.perform(post("/api/v1/projects/{id}/issues", pid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                r.getResponse().getContentAsString(), java.util.Map.class).get("data");
        return ((Number) data.get("id")).longValue();
    }

    private void setMilestone(String token, Long pid, Long issueId, Long milestoneId) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("milestoneId", milestoneId);
        mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", pid, issueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void milestoneCrudAndIssueBinding() throws Exception {
        Long m1 = createMilestone(adminToken, projectId, "V1.0 发布", "2026-10-30T18:00:00");

        // 重名 → 409
        mockMvc.perform(post("/api/v1/projects/{id}/milestones", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"V1.0 发布\"}"))
                .andExpect(status().isConflict());

        // 工作项归属（创建后经 update 设置）
        Long issue = createIssue(adminToken, projectId, "MS 目标工作项");
        setMilestone(adminToken, projectId, issue, m1);
        mockMvc.perform(get("/api/v1/projects/{id}/milestones", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalIssues").value(1))
                .andExpect(jsonPath("$.data[0].doneIssues").value(0));

        // 跨项目里程碑 → 404
        Long otherM = createMilestone(adminToken, otherProjectId, "乙里程碑", null);
        var bad = new java.util.HashMap<String, Object>();
        bad.put("milestoneId", otherM);
        mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", projectId, issue)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isNotFound());

        // 删除里程碑 → 工作项退回未归属（列表为空），工作项仍在
        mockMvc.perform(delete("/api/v1/projects/{id}/milestones/{mid}", projectId, m1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{id}/milestones", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}", projectId, issue)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 完成态切换
        Long m2 = createMilestone(adminToken, projectId, "V2.0 规划", null);
        mockMvc.perform(put("/api/v1/projects/{id}/milestones/{mid}", projectId, m2)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MilestoneRequests.UpdateMilestoneRequest("V2.0 规划（完成）", null, null, "DONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void permissionMatrix() throws Exception {
        // MEMBER（无 project:update）创建 → 403；读（无 issue:list）→ 403；未认证 → 401
        mockMvc.perform(post("/api/v1/projects/{id}/milestones", projectId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"成员里程碑\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{id}/milestones", projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{id}/milestones", projectId))
                .andExpect(status().isUnauthorized());
    }
}
