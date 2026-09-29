package com.workflowx.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.issue.dto.CreateIssueLinkRequest;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.LabelRequests;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.service.ProjectService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作项体系增强（V17，Final Edition FP-1）集成测试：
 * 标签 CRUD/项目归属/唯一性、Issue 标签绑定与替换、截止日期语义、
 * 关联（同项目/自关联/重复 409）、全局工作项四视图。
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkItemEnhancementIntegrationTest {

    private static final String ORG_PREFIX = "wi_org_";
    private static final String KEY_PREFIX = "WI";
    private static final String USER_PREFIX = "wiu_";
    private static final String PASSWORD = "WiPass@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.workflowx.org.mapper.OrganizationMapper orgMapper;

    private Long projectId;
    private Long otherProjectId;
    private String adminToken;
    private String memberToken;
    private Long memberUserId;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin@123456");
        String suffix = String.valueOf(System.currentTimeMillis() % 100000000);
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("WI 组织", ORG_PREFIX + suffix, null), 1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("WI 项目甲", KEY_PREFIX + "A" + suffix, orgId, null), 1L).id();
        otherProjectId = projectService.create(
                new CreateProjectRequest("WI 项目乙", KEY_PREFIX + "B" + suffix, orgId, null), 1L).id();

        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@t.local", PASSWORD, "wi-成员"));
        createdUserIds.add(created.id());
        memberUserId = created.id();
        organizationService.addMember(orgId,
                new com.workflowx.org.dto.AddOrganizationMemberRequest(memberUserId, "MEMBER", null));
        memberToken = login(USER_PREFIX + suffix, PASSWORD);
    }

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete("auth:session:" + id);
        }
        createdUserIds.clear();
        orgMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .likeRight(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX));
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
        return (String) ((java.util.Map<?, ?>) response.get("data")).get("accessToken");
    }

    private Long createLabel(String token, Long pid, String name, String color) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/projects/{id}/labels", pid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.CreateLabelRequest(name, color))))
                .andExpect(status().isCreated())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                r.getResponse().getContentAsString(), java.util.Map.class).get("data");
        return ((Number) data.get("id")).longValue();
    }

    private Long createIssue(String token, Long pid, String title, List<Long> labelIds, String dueDate) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", title);
        body.put("type", "TASK");
        body.put("priority", "HIGH");
        if (labelIds != null) body.put("labelIds", labelIds);
        if (dueDate != null) body.put("dueDate", dueDate);
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

    // ===== 标签 =====

    @Test
    void labelCrudAndUniqueness() throws Exception {
        Long labelId = createLabel(adminToken, projectId, "前端", "#409EFF");

        // 重名 → 409
        mockMvc.perform(post("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.CreateLabelRequest("前端", null))))
                .andExpect(status().isConflict());

        // 非法颜色 → 422
        mockMvc.perform(post("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.CreateLabelRequest("坏颜色", "red"))))
                .andExpect(status().isUnprocessableEntity());

        // 列表（读=issue:list）；MEMBER 无该权限 → 403（权限语义验证）
        mockMvc.perform(get("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("前端"));

        // 更新
        mockMvc.perform(put("/api/v1/projects/{id}/labels/{lid}", projectId, labelId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.UpdateLabelRequest("后端", "#67C23A"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("后端"));

        // 跨项目标签操作 → 404（不泄露存在性）
        mockMvc.perform(put("/api/v1/projects/{id}/labels/{lid}", otherProjectId, labelId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.UpdateLabelRequest("x", null))))
                .andExpect(status().isNotFound());

        // 无 issue:update 的 MEMBER 创建标签 → 403
        mockMvc.perform(post("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LabelRequests.CreateLabelRequest("成员标签", null))))
                .andExpect(status().isForbidden());

        // 删除
        mockMvc.perform(delete("/api/v1/projects/{id}/labels/{lid}", projectId, labelId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{id}/labels", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ===== 工作项标签绑定 / 截止日期 =====

    @Test
    void issueLabelBindingAndDueDate() throws Exception {
        Long l1 = createLabel(adminToken, projectId, "紧急修复", "#F56C6C");
        Long l2 = createLabel(adminToken, projectId, "客户反馈", "#E6A23C");

        // 创建带标签+截止日期
        Long issueId = createIssue(adminToken, projectId, "带标签的工作项",
                List.of(l1, l2), "2026-10-15T18:00:00");
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.length()").value(2))
                .andExpect(jsonPath("$.data.labels[0].name").value("紧急修复"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-15T18:00:00"));

        // 跨项目标签 → 404
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(
                                "坏标签工作项", null, IssueType.TASK, IssuePriority.LOW, null, null,
                                null, List.of(999999L)))))
                .andExpect(status().isNotFound());

        // 全量替换标签（保留 l2）+ 清空截止日期
        var body = new java.util.HashMap<String, Object>();
        body.put("labelIds", List.of(l2));
        body.put("clearDueDate", true);
        mockMvc.perform(put("/api/v1/projects/{id}/issues/{iid}", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.length()").value(1))
                .andExpect(jsonPath("$.data.labels[0].name").value("客户反馈"))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());

        // 按标签筛选：l2 命中 1 条，l1 命中 0 条
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("labelId", String.valueOf(l2)))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("labelId", String.valueOf(l1)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ===== 关联 =====

    @Test
    void issueLinkRules() throws Exception {
        Long a = createIssue(adminToken, projectId, "关联甲", null, null);
        Long b = createIssue(adminToken, projectId, "关联乙", null, null);
        Long other = createIssue(adminToken, otherProjectId, "别的项目的工作项", null, null);

        // 建立关联
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueLinkRequest(b, CreateIssueLinkRequest.IssueLinkType.BLOCKS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].otherIssueId").value(b))
                .andExpect(jsonPath("$.data[0].direction").value("OUTGOING"));

        // 重复 → 409
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueLinkRequest(b, CreateIssueLinkRequest.IssueLinkType.BLOCKS))))
                .andExpect(status().isConflict());

        // 自关联 → 400
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueLinkRequest(a, CreateIssueLinkRequest.IssueLinkType.RELATES))))
                .andExpect(status().isBadRequest());

        // 跨项目目标 → 404
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueLinkRequest(other, CreateIssueLinkRequest.IssueLinkType.RELATES))))
                .andExpect(status().isNotFound());

        // 对端视角（INCOMING）
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}/links", projectId, b)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.data[0].otherIssueId").value(a));

        // 解除
        MvcResult lr = mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken)).andReturn();
        var links = (java.util.List<?>) objectMapper.readValue(
                lr.getResponse().getContentAsString(), java.util.Map.class).get("data");
        Long linkId = ((Number) ((java.util.Map<?, ?>) links.get(0)).get("linkId")).longValue();
        mockMvc.perform(delete("/api/v1/projects/{id}/issues/{iid}/links/{lid}", projectId, a, linkId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{id}/issues/{iid}/links", projectId, a)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ===== 全局工作项四视图 =====

    @Test
    void globalWorkItemScopes() throws Exception {
        // admin 创建 3 条：1 条自办（指派给自己）、1 条未指派、1 条在乙项目
        Long i1 = createIssue(adminToken, projectId, "全局视图-自办", null, null);
        createIssue(adminToken, projectId, "全局视图-未指派", null, null);
        createIssue(adminToken, otherProjectId, "全局视图-乙项目", null, null);
        // i1 指派给 admin（userId=1）
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/projects/{id}/issues/{iid}/assignee", projectId, i1)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":1}"))
                .andExpect(status().isOk());

        String auth = "Bearer " + adminToken;
        // assigned：跨项目聚合，至少含本测试的 1 条（自办）
        mockMvc.perform(get("/api/v1/me/work-items").header("Authorization", auth).param("scope", "assigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].projectKey").exists());
        mockMvc.perform(get("/api/v1/me/work-items").header("Authorization", auth)
                        .param("scope", "assigned").param("keyword", "全局视图-自办"))
                .andExpect(jsonPath("$.data.total").value(1));
        // todo：assigned 中未完结的（本条 OPEN → 命中）
        mockMvc.perform(get("/api/v1/me/work-items").header("Authorization", auth)
                        .param("scope", "todo").param("keyword", "全局视图-自办"))
                .andExpect(jsonPath("$.data.total").value(1));
        // created：本测试建的 3 条全命中（keyword 收敛到本项目前缀）
        mockMvc.perform(get("/api/v1/me/work-items").header("Authorization", auth)
                        .param("scope", "created").param("keyword", "全局视图-"))
                .andExpect(jsonPath("$.data.total").value(3));
        // MEMBER 无 issue:list → 403
        mockMvc.perform(get("/api/v1/me/work-items")
                        .header("Authorization", "Bearer " + memberToken).param("scope", "all"))
                .andExpect(status().isForbidden());
        // 未认证 → 401
        mockMvc.perform(get("/api/v1/me/work-items")).andExpect(status().isUnauthorized());
    }
}
