package com.workflowx.testcase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
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
import com.workflowx.testcase.dto.TestCasePageQuery;
import com.workflowx.testcase.dto.UpdateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.UpdateTestCaseRequest;
import com.workflowx.testcase.entity.TestCase;
import com.workflowx.testcase.entity.TestCaseDirectory;
import com.workflowx.testcase.entity.TestCasePriority;
import com.workflowx.testcase.entity.TestCaseStatus;
import com.workflowx.testcase.entity.TestCaseType;
import com.workflowx.testcase.mapper.TestCaseDirectoryMapper;
import com.workflowx.testcase.mapper.TestCaseMapper;
import com.workflowx.testcase.service.TestCaseDirectoryService;
import com.workflowx.testcase.service.TestCaseService;
import com.workflowx.testcase.vo.DirectoryVO;
import com.workflowx.testcase.vo.TestCaseVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试用例库集成测试（Phase 20; ADR-022）：真实 MySQL。
 * 覆盖: 目录树（创建/防环/跨项目 404）、用例编号行锁唯一、数据级读写均成员、
 * 过滤分页、删除目录用例退回未分类。数据隔离: P20TC 前缀，用后清理。
 */
@SpringBootTest
class TestCaseServiceIntegrationTest {

    private static final String KEY_PREFIX = "P20TC";
    private static final String ORG_PREFIX = "ORG_P20TC_";
    private static final String USER_PREFIX = "p20tc_";
    private static final String DIR_PREFIX = "P20 目录";
    private static final String TITLE_PREFIX = "P20 用例";

    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestCaseDirectoryService directoryService;
    @Autowired
    private TestCaseMapper testCaseMapper;
    @Autowired
    private TestCaseDirectoryMapper directoryMapper;
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
    private Long memberId;

    @BeforeEach
    void setUp() {
        ownerId = createTestUser("owner");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "MAIN", ORG_PREFIX + "MAIN", null), ownerId);
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
        projectService.create(new CreateProjectRequest(
                "P20 项目", KEY_PREFIX + "MAIN", orgId, null), ownerId);
        projectId = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "MAIN")).getId();
    }

    @AfterEach
    void cleanup() {
        // 用例/目录随项目级联删除；再清组织与用户
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
        ownerId = null;
        projectId = null;
        memberId = null;
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "TcPass@123", "tc-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private void addMember(Long userId) {
        Long orgId = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "MAIN")).getId();
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(userId, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(userId, "MEMBER"), ownerId);
    }

    private CreateTestCaseRequest caseRequest(String title, Long directoryId) {
        return new CreateTestCaseRequest(title, "前置", "步骤", "预期",
                TestCaseType.FUNCTIONAL, TestCasePriority.HIGH, TestCaseStatus.ACTIVE, directoryId);
    }

    private DirectoryVO createDir(String name, Long parentId) {
        return directoryService.create(projectId,
                new CreateTestCaseDirectoryRequest(name, parentId), ownerId);
    }

    // ===== 目录 =====

    @Test
    void directoryCreateAndTree() {
        DirectoryVO root = createDir(DIR_PREFIX + "-登录", null);
        DirectoryVO child = createDir(DIR_PREFIX + "-登录-异常流", root.id());
        assertEquals(root.id(), child.parentId());
        assertEquals(2, directoryService.list(projectId, ownerId).size());
    }

    @Test
    void directoryCreateRejectsParentFromOtherProject() {
        DirectoryVO rootA = createDir(DIR_PREFIX + "-A", null);
        // 另一项目
        Long ownerId2 = createTestUser("other");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "B", ORG_PREFIX + "B", null), ownerId2);
        Long orgB = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "B")).getId();
        projectService.create(new CreateProjectRequest(
                "P20 项目B", KEY_PREFIX + "B", orgB, null), ownerId2);
        Long projectIdB = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "B")).getId();
        DirectoryVO rootB = directoryService.create(projectIdB,
                new CreateTestCaseDirectoryRequest(DIR_PREFIX + "-B", null), ownerId2);

        // 项目 A 下创建目录，父目录指向项目 B 的目录 → 404
        assertThrows(ResourceNotFoundException.class, () -> directoryService.create(projectId,
                new CreateTestCaseDirectoryRequest(DIR_PREFIX + "-跨项目", rootB.id()), ownerId));
        // 清理 B 项目
        projectMapper.deleteById(projectIdB);
        organizationMapper.deleteById(orgB);
    }

    @Test
    void directoryMoveRejectsSelfAndCycle() {
        DirectoryVO root = createDir(DIR_PREFIX + "-根", null);
        DirectoryVO child = createDir(DIR_PREFIX + "-子", root.id());
        // 移动到自身 → 400
        assertThrows(BusinessException.class, () -> directoryService.update(projectId, root.id(),
                new UpdateTestCaseDirectoryRequest(null, root.id()), ownerId));
        // 移动到自己的子目录 → 成环 400
        assertThrows(BusinessException.class, () -> directoryService.update(projectId, root.id(),
                new UpdateTestCaseDirectoryRequest(null, child.id()), ownerId));
    }

    @Test
    void directoryDeleteCascadesSubdirsAndUncategorizesCases() {
        DirectoryVO root = createDir(DIR_PREFIX + "-删除根", null);
        createDir(DIR_PREFIX + "-删除子", root.id());
        TestCaseVO inDir = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-目录内", root.id()), ownerId);
        TestCaseVO uncategorized = testCaseService.create(projectId,
                caseRequest(TITLE_PREFIX + "-未分类", null), ownerId);

        directoryService.delete(projectId, root.id(), ownerId);

        assertNull(directoryMapper.selectById(root.id()), "目录已删除");
        assertTrue(directoryMapper.selectList(new LambdaQueryWrapper<TestCaseDirectory>()
                        .eq(TestCaseDirectory::getProjectId, projectId)).isEmpty(),
                "子目录级联删除");
        TestCase reloaded = testCaseMapper.selectById(inDir.id());
        assertNull(reloaded.getDirectoryId(), "目录内用例退回未分类");
        assertEquals(TestCaseStatus.ACTIVE, testCaseMapper.selectById(uncategorized.id()).getStatus());
    }

    // ===== 用例 =====

    @Test
    void createShouldAllocateSequentialTestcaseNoAndBindCreator() {
        TestCaseVO first = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-1", null), ownerId);
        TestCaseVO second = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-2", null), ownerId);
        assertEquals(1L, first.testcaseNo());
        assertEquals(2L, second.testcaseNo());
        assertEquals(ownerId, first.createdBy(), "createdBy 自动绑定创建者");
        assertEquals(TestCaseStatus.ACTIVE, first.status());
    }

    @Test
    void createShouldValidateDirectoryAndStatus() {
        DirectoryVO dir = createDir(DIR_PREFIX + "-校验", null);
        // 目录属于本项目 → 成功
        TestCaseVO ok = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-目录内", dir.id()), ownerId);
        assertEquals(dir.id(), ok.directoryId());
        // 目录不存在 → 400
        assertThrows(BusinessException.class, () -> testCaseService.create(projectId,
                caseRequest(TITLE_PREFIX + "-坏目录", 999999999L), ownerId));
        // 创建时 DEPRECATED → 400
        assertThrows(BusinessException.class, () -> testCaseService.create(projectId,
                new CreateTestCaseRequest(TITLE_PREFIX + "-废弃", null, null, null,
                        TestCaseType.FUNCTIONAL, null, TestCaseStatus.DEPRECATED, null), ownerId));
    }

    @Test
    void pageShouldFilterByDirectoryStatusTypeKeyword() {
        DirectoryVO dir = createDir(DIR_PREFIX + "-过滤", null);
        testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-冒烟登录", dir.id()), ownerId);
        testCaseService.create(projectId, new CreateTestCaseRequest(TITLE_PREFIX + "-回归支付",
                null, null, null, TestCaseType.REGRESSION, TestCasePriority.CRITICAL,
                TestCaseStatus.DRAFT, null), ownerId);

        // keyword 过滤
        PageVO<TestCaseVO> byKeyword = testCaseService.page(projectId, new TestCasePageQuery(
                "回归", null, null, null, null, 1, 10), ownerId);
        assertEquals(1L, byKeyword.total());
        // directoryId 过滤
        PageVO<TestCaseVO> byDir = testCaseService.page(projectId, new TestCasePageQuery(
                null, dir.id(), null, null, null, 1, 10), ownerId);
        assertEquals(1L, byDir.total());
        // 未分类（0 = IS NULL）
        PageVO<TestCaseVO> uncategorized = testCaseService.page(projectId, new TestCasePageQuery(
                null, 0L, null, null, null, 1, 10), ownerId);
        assertEquals(1L, uncategorized.total());
        // status+type 组合
        PageVO<TestCaseVO> byStatus = testCaseService.page(projectId, new TestCasePageQuery(
                null, null, TestCaseStatus.DRAFT, TestCaseType.REGRESSION, null, 1, 10), ownerId);
        assertEquals(1L, byStatus.total());
    }

    @Test
    void updateShouldSupportPartialFieldsAndDirectoryMove() {
        DirectoryVO dirA = createDir(DIR_PREFIX + "-A", null);
        DirectoryVO dirB = createDir(DIR_PREFIX + "-B", null);
        TestCaseVO created = testCaseService.create(projectId,
                caseRequest(TITLE_PREFIX + "-原", dirA.id()), ownerId);
        TestCaseVO updated = testCaseService.update(projectId, created.id(),
                new UpdateTestCaseRequest(TITLE_PREFIX + "-改", null, null, null,
                        TestCaseType.REGRESSION, TestCasePriority.CRITICAL, TestCaseStatus.DEPRECATED,
                        dirB.id()), ownerId);
        assertEquals(TITLE_PREFIX + "-改", updated.title());
        assertEquals(TestCaseType.REGRESSION, updated.type());
        assertEquals(TestCaseStatus.DEPRECATED, updated.status());
        assertEquals(dirB.id(), updated.directoryId());
    }

    @Test
    void deleteShouldRemoveCase() {
        TestCaseVO created = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-删除", null), ownerId);
        testCaseService.delete(projectId, created.id(), ownerId);
        assertThrows(ResourceNotFoundException.class,
                () -> testCaseService.getById(projectId, created.id(), ownerId));
    }

    // ===== 数据级权限 =====

    @Test
    void nonMemberReadShouldReturn403BeforeTargetLookup() {
        TestCaseVO created = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-私有", null), ownerId);
        Long outsider = createTestUser("outsider");
        // 数据级 403 先于目标查找（对齐既有语义）
        assertThrows(ForbiddenException.class,
                () -> testCaseService.getById(projectId, created.id(), outsider));
        assertThrows(ForbiddenException.class,
                () -> testCaseService.page(projectId, new TestCasePageQuery(null, null, null, null, null, 1, 10), outsider));
    }

    @Test
    void nonMemberWriteShouldReturn403() {
        Long outsider = createTestUser("writer");
        assertThrows(ForbiddenException.class,
                () -> testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-越权", null), outsider));
    }

    @Test
    void projectMemberReadWriteShouldSucceed() {
        memberId = createTestUser("member");
        addMember(memberId);
        TestCaseVO created = testCaseService.create(projectId,
                caseRequest(TITLE_PREFIX + "-成员创建", null), memberId);
        assertEquals(memberId, created.createdBy());
        TestCaseVO updated = testCaseService.update(projectId, created.id(),
                new UpdateTestCaseRequest(TITLE_PREFIX + "-成员编辑", null, null, null, null, null, null, null),
                memberId);
        assertTrue(updated.title().endsWith("成员编辑"));
        testCaseService.delete(projectId, created.id(), memberId);
    }

    @Test
    void crossProjectTestCaseAccessShouldReturn404() {
        DirectoryVO dirA = createDir(DIR_PREFIX + "-A", null);
        TestCaseVO caseA = testCaseService.create(projectId, caseRequest(TITLE_PREFIX + "-A项目", dirA.id()), ownerId);
        // 另一项目
        Long ownerId2 = createTestUser("other2");
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + "C", ORG_PREFIX + "C", null), ownerId2);
        Long orgC = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, ORG_PREFIX + "C")).getId();
        projectService.create(new CreateProjectRequest(
                "P20 项目C", KEY_PREFIX + "C", orgC, null), ownerId2);
        Long projectIdC = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getKey, KEY_PREFIX + "C")).getId();

        // 项目 C 路径 + 项目 A 的用例 id → 404（且 C 的 owner2 非 A 成员但 Owner2 对 C 有 membership，故先 project C 校验，用例不存在于 C → 404）
        assertThrows(ResourceNotFoundException.class,
                () -> testCaseService.getById(projectIdC, caseA.id(), ownerId2));
        // 清理
        projectMapper.deleteById(projectIdC);
        organizationMapper.deleteById(orgC);
    }
}
