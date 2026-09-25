package com.workflowx.testplan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.issue.entity.IssuePriority;
import com.workflowx.issue.entity.IssueType;
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
import com.workflowx.testcase.dto.CreateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCaseDirectory;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testcase.mapper.TestCaseDirectoryMapper;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testcase.service.TestCaseDirectoryService;
import com.workflowx.testcase.service.TestCaseService;
import com.workflowx.testcase.vo.TestCaseVO;
import com.workflowx.testplan.dto.AddTestPlanItemsRequest;
import com.workflowx.testplan.dto.CreateTestPlanRequest;
import com.workflowx.testplan.dto.ExecuteItemRequest;
import com.workflowx.testplan.dto.UpdateTestPlanRequest;
import com.workflowx.testplan.entity.TestPlan;
import com.workflowx.testplan.entity.TestPlanItem;
import com.workflowx.testplan.service.TestPlanService;
import com.workflowx.testplan.vo.TestPlanItemVO;
import com.workflowx.testplan.vo.TestPlanVO;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试计划集成测试（Phase 21 / V1.1; ADR-023）：真实 MySQL。
 * 覆盖: 计划 CRUD/状态流/条目增删/执行打结果/Bug 关联校验/COMPLETED 拒绝/数据级 403。
 * 数据隔离: P21TP 前缀，用后清理。
 */
@SpringBootTest
class TestPlanServiceIntegrationTest {

    private static final String KEY_PREFIX = "P21TP";
    private static final String ORG_PREFIX = "ORG_P21TP_";
    private static final String USER_PREFIX = "p21tp_";

    @Autowired
    private TestPlanService testPlanService;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestCaseDirectoryService directoryService;
    @Autowired
    private TestCaseDirectoryMapper directoryMapper;
    @Autowired
    private TestCaseMapper testCaseMapper;
    @Autowired
    private com.workflowx.testplan.mapper.TestPlanItemMapper testPlanItemMapper;
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

    private final List<Long> createdUserIds = new java.util.ArrayList<>();
    private Long ownerId;
    private Long projectId;
    private Long dirId;
    private Long caseId1;
    private Long caseId2;
    private Long caseId3;

    @BeforeEach
    void setUp() {
        ownerId = createTestUser("owner");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "MAIN", ORG_PREFIX + "MAIN", null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
        projectService.create(new CreateProjectRequest(
                "P21 项目", KEY_PREFIX + "MAIN", orgId, null), ownerId);
        projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "MAIN")).getId();
        // 用例库: 3 条用例 + 一个目录
        directoryService.create(projectId,
                new CreateTestCaseDirectoryRequest("P21 目录", null), ownerId);
        dirId = directoryMapper.selectList(new LambdaQueryWrapper<TestCaseDirectory>()
                .eq(TestCaseDirectory::getProjectId, projectId)).get(0).getId();
        caseId1 = testCaseService.create(projectId, caseRequest("P21 用例1", dirId), ownerId).id();
        caseId2 = testCaseService.create(projectId, caseRequest("P21 用例2", dirId), ownerId).id();
        caseId3 = testCaseService.create(projectId, caseRequest("P21 用例3", null), ownerId).id();
    }

    @AfterEach
    void cleanup() {
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
        ownerId = null;
        projectId = null;
        dirId = null;
        caseId1 = caseId2 = caseId3 = null;
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "TpPass@123", "tp-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private com.workflowx.testcase.dto.CreateTestCaseRequest caseRequest(String title, Long directoryId) {
        return new com.workflowx.testcase.dto.CreateTestCaseRequest(title, null, null, null,
                TestCaseType.FUNCTIONAL, TestCasePriority.MEDIUM, TestCaseStatus.ACTIVE, directoryId);
    }

    private TestPlanVO createPlan(String name) {
        return testPlanService.create(projectId, new CreateTestPlanRequest(name), ownerId);
    }

    private void addAllCases(Long planId) {
        testPlanService.addItems(projectId, planId,
                new AddTestPlanItemsRequest(List.of(caseId1, caseId2, caseId3)), ownerId);
    }

    // ===== 计划 CRUD =====

    @Test
    void createShouldDefaultNotStarted() {
        TestPlanVO plan = createPlan("P21 计划");
        assertEquals(TestPlan.TestPlanStatus.NOT_STARTED, plan.status());
        assertEquals(0, plan.total());
        assertEquals(ownerId, plan.createdBy());
    }

    @Test
    void updateShouldChangeNameAndStatus() {
        TestPlanVO plan = createPlan("P21 计划A");
        TestPlanVO updated = testPlanService.update(projectId, plan.id(),
                new UpdateTestPlanRequest("P21 计划B", "RUNNING"), ownerId);
        assertEquals("P21 计划B", updated.name());
        assertEquals(TestPlan.TestPlanStatus.RUNNING, updated.status());
    }

    @Test
    void deleteShouldRemovePlanWithItems() {
        TestPlanVO plan = createPlan("P21 待删");
        addAllCases(plan.id());
        testPlanService.delete(projectId, plan.id(), ownerId);
        assertThrows(ResourceNotFoundException.class,
                () -> testPlanService.get(projectId, plan.id(), ownerId));
        // 条目级联删除（用例本身在用例库，不受计划删除影响）
        assertEquals(0, testPlanItemMapper.selectCount(
                new LambdaQueryWrapper<TestPlanItem>().eq(TestPlanItem::getPlanId, plan.id())));
    }

    // ===== 条目 =====

    @Test
    void addItemsShouldSkipDuplicatesAndRejectForeignCases() {
        TestPlanVO plan = createPlan("P21 添加");
        assertEquals(3, testPlanService.addItems(projectId, plan.id(),
                new AddTestPlanItemsRequest(List.of(caseId1, caseId2, caseId3)), ownerId));
        // 重复添加 → 跳过，不报错
        assertEquals(0, testPlanService.addItems(projectId, plan.id(),
                new AddTestPlanItemsRequest(List.of(caseId1)), ownerId));
        // 非本项目用例 → 400
        Long ownerId2 = createTestUser("other");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "B", ORG_PREFIX + "B", null), ownerId2);
        Long orgB = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "B")).getId();
        projectService.create(new CreateProjectRequest(
                "P21 项目B", KEY_PREFIX + "B", orgB, null), ownerId2);
        Long projectIdB = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "B")).getId();
        TestCaseVO foreignCase = testCaseService.create(projectIdB,
                caseRequest("P21 用例B", null), ownerId2);
        assertThrows(BusinessException.class, () -> testPlanService.addItems(projectId, plan.id(),
                new AddTestPlanItemsRequest(List.of(foreignCase.id())), ownerId));
        // 清理
        projectMapper.deleteById(projectIdB);
        organizationMapper.deleteById(orgB);
    }

    @Test
    void itemsShouldOrderByCaseNoWithCaseDetails() {
        TestPlanVO plan = createPlan("P21 条目");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        assertEquals(3, items.size());
        assertEquals(1L, items.get(0).testcaseNo());
        assertEquals("P21 用例1", items.get(0).caseTitle());
        assertEquals(TestPlanItem.ItemResult.PENDING, items.get(0).result());
    }

    @Test
    void removeItemShouldWork() {
        TestPlanVO plan = createPlan("P21 移除");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        testPlanService.removeItem(projectId, plan.id(), items.get(0).id(), ownerId);
        assertEquals(2, testPlanService.items(projectId, plan.id(), ownerId).size());
    }

    // ===== 执行 =====

    @Test
    void executeShouldSetResultAndAutoTransitionToRunning() {
        TestPlanVO plan = createPlan("P21 执行");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        TestPlanItemVO executed = testPlanService.execute(projectId, plan.id(), items.get(0).id(),
                new ExecuteItemRequest(TestPlanItem.ItemResult.PASS, "通过", null), ownerId);
        assertEquals(TestPlanItem.ItemResult.PASS, executed.result());
        assertEquals(ownerId, executed.executedBy());
        // 首次执行自动 NOT_STARTED → RUNNING
        assertEquals(TestPlan.TestPlanStatus.RUNNING,
                testPlanService.get(projectId, plan.id(), ownerId).status());
    }

    @Test
    void executeFailShouldLinkIssue() {
        Long memberId = createTestUser("member");
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(memberId, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(memberId, "MEMBER"), ownerId);
        IssueVO issue = issueService.create(projectId, new com.workflowx.issue.dto.CreateIssueRequest(
                "P21 缺陷", "描述", IssueType.BUG, IssuePriority.HIGH,
                null, memberId), ownerId);

        TestPlanVO plan = createPlan("P21 关联Bug");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        TestPlanItemVO executed = testPlanService.execute(projectId, plan.id(), items.get(0).id(),
                new ExecuteItemRequest(TestPlanItem.ItemResult.FAIL, "登录失败", issue.id()), memberId);
        assertEquals(TestPlanItem.ItemResult.FAIL, executed.result());
        assertEquals(issue.id(), executed.issueId());
        assertEquals(memberId, executed.executedBy());
    }

    @Test
    void executeShouldRejectIssueLinkOnPass() {
        TestPlanVO plan = createPlan("P21 通过关联");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        assertThrows(BusinessException.class, () -> testPlanService.execute(projectId, plan.id(),
                items.get(0).id(), new ExecuteItemRequest(TestPlanItem.ItemResult.PASS, null, 1L), ownerId));
    }

    @Test
    void completedPlanShouldRejectExecuteAndAdd() {
        TestPlanVO plan = createPlan("P21 完结");
        addAllCases(plan.id());
        testPlanService.update(projectId, plan.id(),
                new UpdateTestPlanRequest(null, "COMPLETED"), ownerId);
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        assertThrows(BusinessException.class, () -> testPlanService.execute(projectId, plan.id(),
                items.get(0).id(), new ExecuteItemRequest(TestPlanItem.ItemResult.PASS, null, null), ownerId));
        assertThrows(BusinessException.class, () -> testPlanService.addItems(projectId, plan.id(),
                new AddTestPlanItemsRequest(List.of(caseId3)), ownerId));
    }

    @Test
    void statsShouldReflectExecution() {
        TestPlanVO plan = createPlan("P21 统计");
        addAllCases(plan.id());
        List<TestPlanItemVO> items = testPlanService.items(projectId, plan.id(), ownerId);
        testPlanService.execute(projectId, plan.id(), items.get(0).id(),
                new ExecuteItemRequest(TestPlanItem.ItemResult.PASS, null, null), ownerId);
        testPlanService.execute(projectId, plan.id(), items.get(1).id(),
                new ExecuteItemRequest(TestPlanItem.ItemResult.FAIL, "失败", null), ownerId);
        TestPlanVO after = testPlanService.get(projectId, plan.id(), ownerId);
        assertEquals(3, after.total());
        assertEquals(1, after.passed());
        assertEquals(1, after.failed());
        assertEquals(0, after.blocked());
        assertEquals(1, after.pending());
    }

    // ===== 数据级 =====

    @Test
    void nonMemberShouldReturn403BeforeTargetLookup() {
        TestPlanVO plan = createPlan("P21 私有");
        Long outsider = createTestUser("outsider");
        assertThrows(ForbiddenException.class,
                () -> testPlanService.get(projectId, plan.id(), outsider));
        assertThrows(ForbiddenException.class,
                () -> testPlanService.page(projectId, 1, 10, outsider));
        assertThrows(ForbiddenException.class,
                () -> testPlanService.delete(projectId, plan.id(), outsider));
    }

    @Test
    void crossProjectPlanAccessShouldReturn404() {
        TestPlanVO plan = createPlan("P21 A项目");
        Long ownerId2 = createTestUser("other2");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "C", ORG_PREFIX + "C", null), ownerId2);
        Long orgC = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "C")).getId();
        projectService.create(new CreateProjectRequest(
                "P21 项目C", KEY_PREFIX + "C", orgC, null), ownerId2);
        Long projectIdC = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "C")).getId();
        // 项目 C owner（非本项目成员但本项目路径）→ 404（目标不在 C）
        assertThrows(ResourceNotFoundException.class,
                () -> testPlanService.get(projectIdC, plan.id(), ownerId2));
        projectMapper.deleteById(projectIdC);
        organizationMapper.deleteById(orgC);
    }
}
