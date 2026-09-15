package com.workflowx.project;

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
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.dto.UpdateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.entity.ProjectStatus;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.service.ProjectService;
import com.workflowx.project.vo.ProjectVO;
import com.workflowx.user.dto.CreateUserRequest;
import com.workflowx.user.entity.User;
import com.workflowx.user.mapper.UserMapper;
import com.workflowx.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 项目领域集成测试（P5-01）：真实 MySQL。
 * 数据隔离: 项目 P4_TEST 前缀 key、组织 ORG_P5_TEST_*、用户 p5_proj_test_ 前缀，用后清理。
 */
@SpringBootTest
class ProjectServiceIntegrationTest {

    private static final String KEY_PREFIX = "P5TEST";
    private static final String ORG_PREFIX = "ORG_P5_TEST_";
    private static final String USER_PREFIX = "p5_proj_test_";

    @Autowired
    private ProjectService projectService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    private final java.util.List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        projectMapper.delete(new LambdaQueryWrapper<Project>().likeRight(Project::getKey, KEY_PREFIX));
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "ProjPass@123", "proj-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    /** 组织 + 创建者（OWNER 成员）；key 以 suffix 大写保证唯一 */
    private Long createTestOrgWithOwner(String suffix) {
        Long ownerId = createTestUser("owner" + suffix);
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + suffix, ORG_PREFIX + suffix, null), ownerId);
        return ownerId;
    }

    private CreateProjectRequest createRequest(String suffix, Long orgId) {
        return new CreateProjectRequest("测试项目-" + suffix, KEY_PREFIX + suffix, orgId, "desc");
    }

    // ===== 创建 =====

    @Test
    void createByOrgOwnerShouldSucceedWithDefaults() {
        Long ownerId = createTestOrgWithOwner("CREATE");
        ProjectVO created = projectService.create(createRequest("CREATE", orgIdFor("CREATE")), ownerId);
        assertEquals(ProjectStatus.ACTIVE, created.status());
        assertEquals(ownerId, created.ownerId());
        assertEquals(KEY_PREFIX + "CREATE", created.key());
    }

    @Test
    void createByNonMemberShouldThrow403() {
        Long ownerId = createTestOrgWithOwner("NOM");
        Long outsider = createTestUser("outsider");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> projectService.create(createRequest("NOM", orgIdFor("NOM")), outsider));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void createByAddedOrgMemberShouldSucceed() {
        Long ownerId = createTestOrgWithOwner("ADDM");
        Long orgId = orgIdFor("ADDM");
        Long member = createTestUser("member");
        organizationService.addMember(orgId, new AddOrganizationMemberRequest(member, "MEMBER", null));
        ProjectVO created = projectService.create(createRequest("ADDM", orgId), member);
        assertEquals(ProjectStatus.ACTIVE, created.status());
    }

    @Test
    void createDuplicateKeyShouldThrow409() {
        Long ownerId = createTestOrgWithOwner("DUPK");
        projectService.create(createRequest("DUPK", orgIdFor("DUPK")), ownerId);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                projectService.create(new CreateProjectRequest("另一项目", KEY_PREFIX + "DUPK",
                        orgIdFor("DUPK"), null), ownerId));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void createWithMissingOrgShouldThrow404() {
        Long ownerId = createTestUser("noorg");
        assertThrows(ResourceNotFoundException.class,
                () -> projectService.create(createRequest("NOORG", 999999999L), ownerId));
    }

    // ===== 查询 =====

    @Test
    void pageShouldFilterKeywordStatusAndOrderStably() {
        Long ownerId = createTestOrgWithOwner("PG");
        Long orgId = orgIdFor("PG");
        projectService.create(createRequest("PGA", orgId), ownerId);
        ProjectVO archived = projectService.create(createRequest("PGB", orgId), ownerId);
        projectService.updateStatus(archived.id(), ProjectStatus.ARCHIVED, ownerId);

        PageVO<ProjectVO> active = projectService.page(KEY_PREFIX + "PG", ProjectStatus.ACTIVE, orgId, 1, 10);
        assertEquals(1, active.total());
        assertEquals("P5TESTPGA", active.list().get(0).key());

        PageVO<ProjectVO> all = projectService.page(KEY_PREFIX + "PG", null, orgId, 1, 10);
        assertEquals(2, all.total());

        PageVO<ProjectVO> run1 = projectService.page(KEY_PREFIX + "PG", null, orgId, 1, 10);
        PageVO<ProjectVO> run2 = projectService.page(KEY_PREFIX + "PG", null, orgId, 1, 10);
        assertEquals(run1.list().stream().map(ProjectVO::id).toList(),
                run2.list().stream().map(ProjectVO::id).toList(), "排序必须稳定");
    }

    @Test
    void getByIdMissingShouldThrow404() {
        assertThrows(ResourceNotFoundException.class, () -> projectService.getById(999999999L));
    }

    // ===== 更新 =====

    @Test
    void updateByOrgMemberShouldChangeNameNotKey() {
        Long ownerId = createTestOrgWithOwner("UPD");
        Long orgId = orgIdFor("UPD");
        ProjectVO created = projectService.create(createRequest("UPD", orgId), ownerId);
        ProjectVO updated = projectService.update(created.id(),
                new UpdateProjectRequest("改名项目", "new"), ownerId);
        assertEquals("改名项目", updated.name());
        assertEquals(KEY_PREFIX + "UPD", updated.key());
    }

    @Test
    void updateByNonMemberShouldThrow403() {
        Long ownerId = createTestOrgWithOwner("UPDN");
        Long orgId = orgIdFor("UPDN");
        ProjectVO created = projectService.create(createRequest("UPDN", orgId), ownerId);
        Long outsider = createTestUser("updnout");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> projectService.update(created.id(), new UpdateProjectRequest("x", null), outsider));
        assertEquals(403, ex.getStatus());
    }

    // ===== 状态/归档 =====

    @Test
    void archiveAndRestoreShouldWorkWithMembershipCheck() {
        Long ownerId = createTestOrgWithOwner("ARC");
        Long orgId = orgIdFor("ARC");
        ProjectVO created = projectService.create(createRequest("ARC", orgId), ownerId);

        assertEquals(ProjectStatus.ARCHIVED,
                projectService.updateStatus(created.id(), ProjectStatus.ARCHIVED, ownerId).status());

        Long outsider = createTestUser("arcout");
        assertThrows(ForbiddenException.class,
                () -> projectService.updateStatus(created.id(), ProjectStatus.ACTIVE, outsider));

        assertEquals(ProjectStatus.ACTIVE,
                projectService.updateStatus(created.id(), ProjectStatus.ACTIVE, ownerId).status());
    }

    // ===== org 删除级联 =====

    @Test
    void orgDeletionShouldCascadeProjects() {
        Long ownerId = createTestOrgWithOwner("CASC");
        Long orgId = orgIdFor("CASC");
        ProjectVO created = projectService.create(createRequest("CASC", orgId), ownerId);
        organizationMapper.deleteById(orgId);
        assertThrows(ResourceNotFoundException.class, () -> projectService.getById(created.id()));
    }

    private Long orgIdFor(String suffix) {
        return organizationMapper.selectOne(new LambdaQueryWrapper<com.workflowx.org.entity.Organization>()
                .eq(com.workflowx.org.entity.Organization::getCode, ORG_PREFIX + suffix)).getId();
    }
}
