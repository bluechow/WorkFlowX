package com.workflowx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowx.auth.dto.LoginRequest;
import com.workflowx.common.security.AuthSessionService;
import com.workflowx.issue.entity.Issue;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.AddProjectMemberRequest;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人中心（Phase A-④）集成测试：资料修改 / 密码修改（含会话作废）/ 我的待办。
 * 前缀 PF- 隔离，@AfterEach 级联清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfilePasswordTodoIntegrationTest {

    private static final String USER_PREFIX = "pfu_";
    private static final String ORG_PREFIX = "pf_org_";
    private static final String KEY_PREFIX = "PF";
    private static final String PASSWORD = "OldPass@123";
    private static final String NEW_PASSWORD = "NewPass@456";

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
    private com.workflowx.org.mapper.OrganizationMapper organizationMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private IssueMapper issueMapper;

    @Autowired
    private com.workflowx.issue.service.IssueService issueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuthSessionService authSessionService;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdIssueIds = new ArrayList<>();
    private final List<Long> createdProjectIds = new ArrayList<>();

    @BeforeEach
    void purgeLeftovers() {
        // 防御：清理上一次失败运行可能遗留的 PF- 前缀数据（组织删除级联项目/成员）
        organizationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .likeRight(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX));
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, USER_PREFIX));
    }

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            redisTemplate.delete("auth:session:" + id);
        }
        createdUserIds.clear();
        for (Long id : createdIssueIds) {
            issueMapper.deleteById(id);
        }
        createdIssueIds.clear();
        for (Long id : createdProjectIds) {
            projectMapper.deleteById(id);
        }
        createdProjectIds.clear();
        organizationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
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
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("data");
        return (String) data.get("accessToken");
    }

    /** 创建独立用户（密码固定 OldPass@123）并返回 userId */
    private Long createSelfUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", PASSWORD, "pf-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    // ===== 资料修改 =====

    @Test
    void updateProfileChangesEmailAndNickname_andRejectsDuplicateEmail() throws Exception {
        Long meId = createSelfUser("A1");
        Long otherId = createSelfUser("A2");
        String token = login(USER_PREFIX + "A1", PASSWORD);

        // 正常修改
        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pfu_a1-new@test.local","nickname":"新昵称"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("pfu_a1-new@test.local"))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        // email 被他人占用 → 400
        String otherEmail = USER_PREFIX + "A2@test.local";
        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + otherEmail + "\",\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest());

        // 非法 email → 422（Bean Validation）
        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"nickname\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 未认证 → 401
        mockMvc.perform(put("/api/v1/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\",\"nickname\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        assertNotEquals(meId, otherId);
    }

    // ===== 密码修改 =====

    @Test
    void changePasswordVerifiesOldAndInvalidatesSession() throws Exception {
        createSelfUser("P1");
        String token = login(USER_PREFIX + "P1", PASSWORD);

        // 旧密码错误 → 400
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"WrongOld@1\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        // 新密码不满足规则 → 422
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 正确修改 → 200，且旧会话被作废（再访问 /auth/me → 401）
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // 新密码可登录；旧密码不可登录
        login(USER_PREFIX + "P1", NEW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(USER_PREFIX + "P1", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ===== 我的待办 =====

    @Test
    void todoIssuesReturnOnlyOpenOnesAssignedToMe() throws Exception {
        Long meId = createSelfUser("T1");
        Long otherId = createSelfUser("T2");
        // 创建者自动成为组织 OWNER / 项目 OWNER 成员，无需自加
        Long orgId = organizationService.create(
                new CreateOrganizationRequest("PF 组织", ORG_PREFIX + "T1", null), meId).id();
        Long projectId = projectService.create(
                new CreateProjectRequest("PF 项目", KEY_PREFIX + "T1", orgId, null), meId).id();
        createdProjectIds.add(projectId);
        // 规则链：先入组织，再入项目
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(otherId, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(otherId, "MEMBER"), meId);

        String token = login(USER_PREFIX + "T1", PASSWORD);

        // 经 Service 层直建（本测试对象是待办端点；MEMBER 无 issue:create authority，HTTP 建单会 403）
        createdIssueIds.add(createIssue(projectId, "待办甲", IssueStatus.OPEN, meId, meId));
        createdIssueIds.add(createIssue(projectId, "已关闭不算待办", IssueStatus.CLOSED, meId, meId));
        createdIssueIds.add(createIssue(projectId, "处理中乙", IssueStatus.IN_PROGRESS, meId, meId));
        createdIssueIds.add(createIssue(projectId, "别人的活", IssueStatus.OPEN, otherId, otherId));

        MvcResult result = mockMvc.perform(get("/api/v1/me/todo-issues")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        @SuppressWarnings("unchecked")
        var list = (java.util.List<java.util.Map<String, Object>>) response.get("data");
        assertEquals(2, list.size());
        assertEquals(KEY_PREFIX + "T1", ((java.util.Map<?, ?>) list.get(0)).get("projectKey"));
        // 未认证 → 401
        mockMvc.perform(get("/api/v1/me/todo-issues")).andExpect(status().isUnauthorized());
    }

    private Long createIssue(Long projectId, String title,
                             IssueStatus status, Long assigneeId, Long reporterId) {
        var vo = issueService.create(projectId, new com.workflowx.issue.dto.CreateIssueRequest(
                title, null, IssueType.BUG, IssuePriority.MEDIUM, IssueSeverity.S3, assigneeId), reporterId);
        if (status != IssueStatus.OPEN) {
            Issue issue = issueMapper.selectById(vo.id());
            issue.setStatus(status);
            issueMapper.updateById(issue);
        }
        return vo.id();
    }
}
