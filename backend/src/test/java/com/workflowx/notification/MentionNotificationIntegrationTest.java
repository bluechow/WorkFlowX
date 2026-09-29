package com.workflowx.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @提及通知（Final Edition FP-7A）集成测试：
 * 评论含 @用户名 → 被提及者收到 ISSUE_MENTIONED；「@我的」筛选（type=ISSUE_MENTIONED）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MentionNotificationIntegrationTest {

    private static final String ORG_PREFIX = "mn_org_";
    private static final String KEY_PREFIX = "MN";

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
    private String mentionedToken;
    private String mentionedUsername;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin@123456");
        String suffix = String.valueOf(System.currentTimeMillis() % 100000000);
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("提及组织", ORG_PREFIX + suffix, null), 1L).id();
        projectId = projectService.create(
                new CreateProjectRequest("提及项目", KEY_PREFIX + suffix, orgId, null), 1L).id();
        // 动态被提及者：入组织+项目，但既非 assignee 也非 reporter（验证纯 @提及路径）
        mentionedUsername = "mn_user" + suffix;
        var createdUser = userService.create(new com.workflowx.user.dto.CreateUserRequest(
                mentionedUsername, mentionedUsername + "@t.local", "MnPass@123", "被提及者"));
        organizationService.addMember(orgId,
                new com.workflowx.org.dto.AddOrganizationMemberRequest(createdUser.id(), "MEMBER", null));
        projectMemberServiceRef.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(createdUser.id(), "MEMBER"), 1L);
        mentionedToken = login(mentionedUsername, "MnPass@123");
    }

    @Autowired
    private com.workflowx.project.service.ProjectMemberService projectMemberServiceRef;

    @Autowired
    private com.workflowx.user.service.UserService userService;

    @Autowired
    private com.workflowx.user.mapper.UserMapper userMapper;

    @Autowired
    private com.workflowx.common.security.AuthSessionService sessionService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplateRef;

    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return jdbcTemplateRef;
    }

    @AfterEach
    void cleanup() {
        orgMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .likeRight(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX));
        if (mentionedUsername != null) {
            var mnUser = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.user.entity.User>()
                            .eq(com.workflowx.user.entity.User::getUsername, mentionedUsername));
            if (mnUser != null) {
                sessionService.deleteSession(mnUser.getId());
                // 通知为逻辑引用（无 FK）——删用户前先清其通知，防跨运行残留
                jdbcTemplate().update("DELETE FROM notifications WHERE recipient_id = ?", mnUser.getId());
            }
            userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.user.entity.User>()
                    .likeRight(com.workflowx.user.entity.User::getUsername, "mn_user"));
        }
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
    void mentionInCommentProducesNotificationAndTypeFilter() throws Exception {
        // Issue（reporter=admin，无分派——被提及者不在评论通知名单内）
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"提及目标\",\"type\":\"TASK\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var data = (java.util.Map<?, ?>) objectMapper.readValue(
                created.getResponse().getContentAsString(), java.util.Map.class).get("data");
        Long issueId = ((Number) data.get("id")).longValue();

        // admin 评论 @被提及者（用户名提及）
        mockMvc.perform(post("/api/v1/projects/{id}/issues/{iid}/comments", projectId, issueId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"@" + mentionedUsername + " 请确认这个方案\"}"))
                .andExpect(status().isCreated());

        // 被提及者收到恰好 1 条 ISSUE_MENTIONED（非 assignee/reporter，不掺杂评论通知）
        MvcResult list = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentionedToken)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        var page = (java.util.Map<?, ?>) objectMapper.readValue(
                list.getResponse().getContentAsString(), java.util.Map.class).get("data");
        var items = (java.util.List<?>) page.get("list");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.size(), "被提及者应只收到 1 条通知");
        org.junit.jupiter.api.Assertions.assertEquals("ISSUE_MENTIONED",
                ((java.util.Map<?, ?>) items.get(0)).get("type"));

        // type 筛选「@我的」→ 命中该条
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentionedToken)
                        .param("type", "ISSUE_MENTIONED").param("size", "20"))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].type").value("ISSUE_MENTIONED"));
    }
}
