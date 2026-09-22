package com.workflowx.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.dashboard.service.DashboardService;
import com.workflowx.dashboard.vo.DashboardOverviewVO;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueSeverity;
import com.workflowx.issue.entity.IssueStatus;
import com.workflowx.issue.entity.IssueType;
import com.workflowx.issue.mapper.IssueMapper;
import com.workflowx.issue.service.IssueService;
import com.workflowx.issue.vo.IssueVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard 领域集成测试（P10-14; ADR-020）：真实 MySQL。
 * 覆盖: 指标正确性（多项目/多状态/多类型）/空数据/角色数据范围（ADMIN 全局 vs 普通用户仅成员项目）/趋势补零。
 * 数据隔离: P10DS 前缀，用后清理。
 */
@SpringBootTest
class DashboardServiceIntegrationTest {

    private static final String KEY_PREFIX = "P10DS";
    private static final String ORG_PREFIX = "ORG_P10DS_";
    private static final String USER_PREFIX = "p10ds_";

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private IssueService issueService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private IssueMapper issueMapper;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        issueMapper.delete(new LambdaQueryWrapper<com.workflowx.issue.entity.Issue>()
                .likeRight(com.workflowx.issue.entity.Issue::getTitle, "P10DS-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "DashPass@123", "ds-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    @Test
    void adminScopeShouldAggregateAllProjects() {
        Long admin = createTestUser("admin");
        Long outsider = createTestUser("outsider");
        Long orgIdA = createOrgProject(admin, "A");
        Long orgIdB = createOrgProject(admin, "B");
        Long projectA = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "A")).getId();
        Long projectB = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "B")).getId();

        issueService.create(projectA, req("P10DS-BUG", IssueType.BUG, IssueSeverity.S1, IssuePriority.HIGH), admin);
        issueService.create(projectA, req("P10DS-TASK", IssueType.TASK, null, IssuePriority.LOW), admin);
        issueService.create(projectB, req("P10DS-BUG2", IssueType.BUG, IssueSeverity.S2, IssuePriority.URGENT), admin);

        DashboardOverviewVO vo = dashboardService.overview(admin, List.of("ADMIN"));
        // 项目统计（含既有 seed/其他测试残留也计入 ADMIN 全局范围——断言相对关系而非绝对数）
        assertTrue(vo.projects().total() >= 2);
        DashboardOverviewVO.IssueStats issues = vo.issues();
        long bugTotal = issues.byType().getOrDefault("BUG", 0L);
        assertTrue(bugTotal >= 2, "BUG 计数含两个测试 BUG");
        assertTrue(issues.byStatus().getOrDefault("OPEN", 0L) >= 3);
        assertTrue(issues.bugCount() >= 2);
        assertTrue(issues.bySeverity().containsKey("S1") || issues.bySeverity().containsKey("S2"));

        // outsider（非任何项目成员）scope → 只统计其成员项目 = 0
        DashboardOverviewVO scoped = dashboardService.overview(outsider, List.of("MEMBER"));
        assertEquals(0, scoped.projects().total(), "非成员无项目数据（数据范围隔离）");
        assertEquals(0, scoped.issues().total());

        // 普通 USER 角色（admin 是本项目 OWNER=成员）→ 范围收敛到其项目
        DashboardOverviewVO memberScope = dashboardService.overview(admin, List.of("ADMIN", "MEMBER"));
        assertTrue(memberScope.issues().total() <= issues.total());
    }

    @Test
    void memberScopeShouldOnlySeeOwnProjects() {
        Long owner = createTestUser("owner");
        Long member = createTestUser("member");
        Long stranger = createTestUser("stranger");
        createOrgProject(owner, "M");
        Long projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "M")).getId();
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "M")).getId();
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(member, "MEMBER"), owner);
        issueService.create(projectId, req("P10DS-成员可见", IssueType.BUG, IssueSeverity.S3, null), owner);

        DashboardOverviewVO memberView = dashboardService.overview(member, List.of("MEMBER"));
        assertEquals(1, memberView.projects().total(), "member 只见其成员项目");
        assertEquals(1, memberView.issues().total());
        assertTrue(memberView.issues().bySeverity().containsKey("S3"));

        DashboardOverviewVO strangerView = dashboardService.overview(stranger, List.of("MEMBER"));
        assertEquals(0, strangerView.projects().total(), "非成员项目不可见（不因同组织/系统而泄漏）");
        assertEquals(0, strangerView.issues().total());
    }

    @Test
    void emptyScopeShouldReturnZerosAndTrendPadding() {
        Long nobody = createTestUser("nobody");
        DashboardOverviewVO vo = dashboardService.overview(nobody, List.of("MEMBER"));
        assertEquals(0, vo.projects().total());
        assertEquals(0, vo.issues().total());
        assertEquals(14, vo.createdTrend().size(), "趋势固定 14 天（含补零）");
        assertTrue(vo.createdTrend().stream().allMatch(p -> p.created() == 0));
    }

    @Test
    void trendShouldIncludeTodayIssue() {
        Long owner = createTestUser("trend");
        createOrgProject(owner, "T");
        Long projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "T")).getId();
        issueService.create(projectId, req("P10DS-趋势", IssueType.TASK, null, null), owner);
        DashboardOverviewVO vo = dashboardService.overview(owner, List.of("MEMBER"));
        assertEquals(14, vo.createdTrend().size());
        long todayCreated = vo.createdTrend().get(13).created();
        assertTrue(todayCreated >= 1, "今日创建的 Issue 出现在趋势末点");
        assertEquals(LocalDate.now().toString(), vo.createdTrend().get(13).date());
    }

    // ===== helpers =====

    private CreateIssueRequest req(String title, IssueType type, IssueSeverity severity, IssuePriority priority) {
        return new CreateIssueRequest(title, null, type, priority, severity, null);
    }

    private Long createOrgProject(Long ownerId, String suffix) {
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + suffix, ORG_PREFIX + suffix, null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + suffix)).getId();
        projectService.create(new CreateProjectRequest(
                "P10DS 项目" + suffix, KEY_PREFIX + suffix, orgId, null), ownerId);
        return orgId;
    }
}
