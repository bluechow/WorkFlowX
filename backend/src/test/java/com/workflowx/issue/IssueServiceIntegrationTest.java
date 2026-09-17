package com.workflowx.issue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.issue.dto.CreateIssueRequest;
import com.workflowx.issue.dto.IssuePageQuery;
import com.workflowx.issue.dto.UpdateIssueRequest;
import com.workflowx.issue.entity.Issue;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 领域集成测试（P6-02/P6-04/P6-05）：真实 MySQL。
 * 数据隔离: 项目 P6TEST 前缀 key、组织 ORG_P6TEST_*、用户 p6test_ 前缀，用后清理。
 */
@SpringBootTest
class IssueServiceIntegrationTest {

    private static final String KEY_PREFIX = "P6TEST";
    private static final String ORG_PREFIX = "ORG_P6TEST_";
    private static final String USER_PREFIX = "p6test_";

    @Autowired
    private IssueService issueService;

    @Autowired
    private com.workflowx.issue.service.WorkflowService workflowService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserService userService;

    @Autowired
    private IssueMapper issueMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private UserMapper userMapper;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        issueMapper.delete(new LambdaQueryWrapper<Issue>().likeRight(Issue::getTitle, "P6-"));
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "IssuePass@123", "iss-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    /** 建 owner 用户 + 组织 + 项目（owner 为项目成员），返回 ownerId */
    private Long createOrgAndProject(String suffix) {
        Long ownerId = createTestUser("owner" + suffix);
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + suffix, ORG_PREFIX + suffix, null), ownerId);
        projectService.create(new CreateProjectRequest(
                "Issue 项目-" + suffix, KEY_PREFIX + suffix, orgIdFor(suffix), null), ownerId);
        return ownerId;
    }

    private Long orgIdFor(String suffix) {
        return organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + suffix)).getId();
    }

    private Long projectIdFor(String suffix) {
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + suffix)).getId();
    }

    private CreateIssueRequest createRequest(String title) {
        return new CreateIssueRequest(title, "P6- 描述", IssueType.BUG,
                IssuePriority.HIGH, IssueSeverity.S2, null);
    }

    private void addProjectMember(Long projectId, Long userId) {
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(userId, "MEMBER"), ownerIdOf(projectId));
    }

    private Long ownerIdOf(Long projectId) {
        return projectMapper.selectById(projectId).getOwnerId();
    }

    // ===== 创建 =====

    @Test
    void createShouldBindReporterAndAssignSequentialIssueNo() {
        Long ownerId = createOrgAndProject("SEQ");
        Long projectId = projectIdFor("SEQ");
        IssueVO first = issueService.create(projectId, createRequest("P6-第一个"), ownerId);
        IssueVO second = issueService.create(projectId, createRequest("P6-第二个"), ownerId);
        assertEquals(1L, first.issueNo());
        assertEquals(2L, second.issueNo());
        assertEquals(ownerId, first.reporterId(), "reporter 自动绑定创建者");
        assertEquals(IssueStatus.OPEN, first.status());
        assertEquals(IssueSeverity.S2, first.severity());
    }

    @Test
    void createByNonProjectMemberShouldThrow403() {
        createOrgAndProject("NOM");
        Long projectId = projectIdFor("NOM");
        Long outsider = createTestUser("nomout");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> issueService.create(projectId, createRequest("P6-越权"), outsider));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void createWithMissingProjectShouldThrow404() {
        Long user = createTestUser("nop404");
        assertThrows(ResourceNotFoundException.class,
                () -> issueService.create(999999999L, createRequest("P6-nop"), user));
    }

    @Test
    void createAssigneeMustBeProjectMember() {
        Long ownerId = createOrgAndProject("ASG");
        Long projectId = projectIdFor("ASG");
        Long orgMemberOnly = createTestUser("orgonly");
        organizationService.addMember(orgIdFor("ASG"),
                new AddOrganizationMemberRequest(orgMemberOnly, "MEMBER", null));

        // 组织成员但非项目成员 → 400
        BusinessException ex400 = assertThrows(BusinessException.class, () -> issueService.create(
                projectId, new CreateIssueRequest("P6-分派", null, IssueType.TASK,
                        IssuePriority.MEDIUM, null, orgMemberOnly), ownerId));
        assertEquals(400, ex400.getStatus(), "assignee 须为项目成员（组织成员不够）");

        // 加入项目成员后成功
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(orgMemberOnly, "MEMBER"), ownerId);
        IssueVO created = issueService.create(projectId,
                new CreateIssueRequest("P6-分派成功", null, IssueType.TASK,
                        IssuePriority.MEDIUM, null, orgMemberOnly), ownerId);
        assertEquals(orgMemberOnly, created.assigneeId());
    }

    @Test
    void severityOnNonBugShouldThrow400() {
        Long ownerId = createOrgAndProject("SEV");
        BusinessException ex = assertThrows(BusinessException.class, () -> issueService.create(
                projectIdFor("SEV"), new CreateIssueRequest("P6-非Bug带severity", null,
                        IssueType.TASK, IssuePriority.MEDIUM, IssueSeverity.S1, null), ownerId));
        assertEquals(400, ex.getStatus());
    }

    // ===== 更新 =====

    @Test
    void updateShouldAllowEditableFieldsAndKeepImmutableOnes() {
        Long ownerId = createOrgAndProject("UPD");
        Long projectId = projectIdFor("UPD");
        IssueVO created = issueService.create(projectId, createRequest("P6-原标题"), ownerId);
        IssueVO updated = issueService.update(projectId, created.id(),
                new UpdateIssueRequest("P6-新标题", "新描述", IssuePriority.URGENT, IssueSeverity.S1, null), ownerId);
        assertEquals("P6-新标题", updated.title());
        assertEquals(IssuePriority.URGENT, updated.priority());
        assertEquals(created.issueNo(), updated.issueNo(), "issue_no 不可变");
        assertEquals(created.projectId(), updated.projectId(), "project 不可变");
        assertEquals(ownerId, updated.reporterId(), "reporter 不可变");
    }

    @Test
    void updateAssigneeMustRevalidateMembership() {
        Long ownerId = createOrgAndProject("UPDA");
        Long projectId = projectIdFor("UPDA");
        Long member = createTestUser("updm");
        organizationService.addMember(orgIdFor("UPDA"),
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(member, "MEMBER"), ownerId);
        IssueVO issue = issueService.create(projectId, createRequest("P6-分派更新"), ownerId);

        // member 移出项目后重新分派 → 400
        projectMemberService.removeMember(projectId, member, ownerId);
        BusinessException ex = assertThrows(BusinessException.class, () -> issueService.update(
                projectId, issue.id(), new UpdateIssueRequest(null, null, null, null, member), ownerId));
        assertEquals(400, ex.getStatus(), "assignee 变更须重新校验项目成员身份");
    }

    @Test
    void updateByNonMemberShouldThrow403() {
        Long ownerId = createOrgAndProject("UPDN");
        Long projectId = projectIdFor("UPDN");
        Long outsider = createTestUser("updn");
        IssueVO created = issueService.create(projectId, createRequest("P6-越权"), ownerId);
        assertThrows(ForbiddenException.class, () -> issueService.update(
                projectId, created.id(), new UpdateIssueRequest("x", null, null, null, null), outsider));
    }

    @Test
    void updateMissingIssueInOtherProjectShouldThrow404() {
        Long ownerId = createOrgAndProject("UP404");
        Long projectIdA = projectIdFor("UP404");
        IssueVO created = issueService.create(projectIdA, createRequest("P6-跨项目"), ownerId);
        Long projectIdB;
        {
            Long ownerIdB = createTestUser("ownerB404");
            organizationService.create(new CreateOrganizationRequest(
                    ORG_PREFIX + "B404", ORG_PREFIX + "B404", null), ownerIdB);
            projectService.create(new CreateProjectRequest(
                    "B 项目", KEY_PREFIX + "B404", orgIdFor("B404"), null), ownerIdB);
            projectIdB = projectIdFor("B404");
        }
        // A 项目的 issue 在 B 项目路径下 → 404（防跨项目访问）
        assertThrows(ResourceNotFoundException.class,
                () -> issueService.getById(projectIdB, created.id()));
        assertThrows(ResourceNotFoundException.class, () -> issueService.update(
                projectIdB, created.id(), new UpdateIssueRequest("x", null, null, null, null), ownerId));
    }

    // ===== 状态（P6-05） =====

    @Test
    void statusTransitionShouldFollowMatrixAndRejectIllegals() {
        // P7: 状态能力迁移至 WorkflowService——主链 OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED
        Long ownerId = createOrgAndProject("ST");
        Long projectId = projectIdFor("ST");
        IssueVO created = issueService.create(projectId, createRequest("P6-状态"), ownerId);
        IssueVO inProgress = workflowService.transition(projectId, created.id(),
                IssueStatus.OPEN, IssueStatus.IN_PROGRESS, ownerId);
        assertEquals(IssueStatus.IN_PROGRESS, inProgress.status());
        IssueVO resolved = workflowService.transition(projectId, inProgress.id(),
                IssueStatus.IN_PROGRESS, IssueStatus.RESOLVED, ownerId);
        IssueVO testing = workflowService.transition(projectId, resolved.id(),
                IssueStatus.RESOLVED, IssueStatus.TESTING, ownerId);
        IssueVO closed = workflowService.transition(projectId, testing.id(),
                IssueStatus.TESTING, IssueStatus.CLOSED, ownerId);
        assertEquals(IssueStatus.CLOSED, closed.status());
    }

    @Test
    void statusTransitionByNonMemberShouldThrow403() {
        Long ownerId = createOrgAndProject("STN");
        Long projectId = projectIdFor("STN");
        Long outsider = createTestUser("stn");
        IssueVO created = issueService.create(projectId, createRequest("P6-状态越权"), ownerId);
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> workflowService.transition(
                projectId, created.id(), IssueStatus.OPEN, IssueStatus.IN_PROGRESS, outsider));
        assertEquals(403, ex.getStatus());
    }

    // ===== 查询（P6-04） =====

    @Test
    void pageShouldSupportAllFiltersAndProjectIsolation() {
        Long ownerId = createOrgAndProject("PG");
        Long projectId = projectIdFor("PG");
        Long member = createTestUser("pgm");
        organizationService.addMember(orgIdFor("PG"),
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId,
                new com.workflowx.project.dto.AddProjectMemberRequest(member, "MEMBER"), ownerId);

        IssueVO bug = issueService.create(projectId, new CreateIssueRequest(
                "P6-登录崩溃", "紧急修复", IssueType.BUG, IssuePriority.URGENT, IssueSeverity.S1, member), ownerId);
        IssueVO task = issueService.create(projectId, new CreateIssueRequest(
                "P6-写文档", null, IssueType.TASK, IssuePriority.LOW, null, null), ownerId);
        workflowService.transition(projectId, task.id(),
                IssueStatus.OPEN, IssueStatus.IN_PROGRESS, ownerId);
        workflowService.transition(projectId, task.id(),
                IssueStatus.IN_PROGRESS, IssueStatus.RESOLVED, ownerId);

        // keyword（标题/描述）
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery("崩溃", null, null, null, null, null, null, null, 1, 10)).total());
        // issueNo 精确
        assertEquals(bug.id(), issueService.page(projectId,
                new IssuePageQuery(null, bug.issueNo(), null, null, null, null, null, null, 1, 10))
                .list().get(0).id());
        // type / priority / severity / status
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, IssueType.BUG, null, null, null, null, null, 1, 10)).total());
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, null, IssuePriority.URGENT, null, null, null, null, 1, 10)).total());
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, IssueSeverity.S1, null, null, null, 1, 10)).total());
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, null, IssueStatus.RESOLVED, null, null, 1, 10)).total());
        // reporter / assignee
        assertEquals(2, issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, null, null, ownerId, null, 1, 10)).total());
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, null, null, null, member, 1, 10)).total());
        // 组合: type=BUG + assignee=member
        assertEquals(1, issueService.page(projectId,
                new IssuePageQuery(null, null, IssueType.BUG, null, null, null, null, member, 1, 10)).total());
        // 分页 size=1
        PageVO<IssueVO> sized = issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, null, null, null, null, 1, 1));
        assertEquals(2, sized.total());
        assertEquals(1, sized.list().size());
        // 项目隔离: 其他项目查不到
        Long ownerId2 = createTestUser("ownerpg2");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "PG2", ORG_PREFIX + "PG2", null), ownerId2);
        projectService.create(new CreateProjectRequest(
                "PG2 项目", KEY_PREFIX + "PG2", orgIdFor("PG2"), null), ownerId2);
        assertEquals(0, issueService.page(projectIdFor("PG2"),
                new IssuePageQuery("崩溃", null, null, null, null, null, null, null, 1, 10)).total());
        // 稳定排序: created_at DESC, id DESC
        PageVO<IssueVO> page = issueService.page(projectId,
                new IssuePageQuery(null, null, null, null, null, null, null, null, 1, 10));
        assertTrue(page.list().get(0).id() > page.list().get(1).id(), "稳定排序 created_at DESC, id DESC");
    }
}
