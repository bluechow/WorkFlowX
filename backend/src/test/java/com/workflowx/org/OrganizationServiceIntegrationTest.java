package com.workflowx.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.common.web.PageVO;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateDepartmentRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.dto.UpdateOrganizationRequest;
import com.workflowx.org.entity.Department;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.entity.OrgMemberType;
import com.workflowx.org.mapper.DepartmentMapper;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.DepartmentService;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.org.vo.OrganizationMemberVO;
import com.workflowx.org.vo.OrganizationVO;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织架构领域集成测试（P4-01）：真实 MySQL（compose :3307）。
 * 数据隔离: 组织 ORG_P4_TEST_* 前缀、用户 p4_org_test_ 前缀，用后清理。
 */
@SpringBootTest
class OrganizationServiceIntegrationTest {

    private static final String ORG_PREFIX = "ORG_P4_TEST_";
    private static final String USER_PREFIX = "p4_org_test_";

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        organizationMapper.delete(new LambdaQueryWrapper<Organization>().likeRight(Organization::getCode, ORG_PREFIX));
        userMapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getUsername, USER_PREFIX));
        createdUserIds.clear();
    }

    private Long createTestUser(String suffix) {
        var created = userService.create(new CreateUserRequest(
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "OrgPass@123", "org-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    private OrganizationVO createTestOrg(String suffix, Long ownerId) {
        return organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + suffix, ORG_PREFIX + suffix, "测试组织"), ownerId);
    }

    // ===== 组织创建 =====

    @Test
    void createShouldMakeOwnerAMemberInSameTransaction() {
        Long ownerId = createTestUser("owner");
        OrganizationVO org = createTestOrg("CREATE", ownerId);
        assertNotNull(org.id());
        assertEquals(ownerId, org.ownerId());
        // 创建者即 OWNER（同事务写入成员表）
        List<OrganizationMemberVO> members = organizationService.listMembers(org.id());
        assertEquals(1, members.size());
        assertEquals(OrgMemberType.OWNER, members.get(0).role());
        assertEquals(ownerId, members.get(0).userId());
    }

    @Test
    void createDuplicateCodeShouldThrow409() {
        Long ownerId = createTestUser("dup");
        createTestOrg("DUP", ownerId);
        BusinessException ex = assertThrows(BusinessException.class, () -> createTestOrg("DUP", ownerId));
        assertEquals(409, ex.getStatus());
    }

    // ===== 查询 =====

    @Test
    void pageShouldMatchKeywordAndOrderStably() {
        Long ownerId = createTestUser("page");
        createTestOrg("PAGEA", ownerId);
        createTestOrg("PAGEB", ownerId);
        PageVO<OrganizationVO> run1 = organizationService.page(ORG_PREFIX + "PAGE", 1, 10);
        PageVO<OrganizationVO> run2 = organizationService.page(ORG_PREFIX + "PAGE", 1, 10);
        assertEquals(2, run1.total());
        assertEquals(run1.list().stream().map(OrganizationVO::id).toList(),
                run2.list().stream().map(OrganizationVO::id).toList(), "排序必须稳定");
    }

    @Test
    void getByIdMissingShouldThrow404() {
        assertThrows(ResourceNotFoundException.class, () -> organizationService.getById(999999999L));
    }

    // ===== 更新/删除 =====

    @Test
    void updateShouldChangeNameButNotCodeOrOwner() {
        Long ownerId = createTestUser("upd");
        OrganizationVO org = createTestOrg("UPD", ownerId);
        OrganizationVO updated = organizationService.update(org.id(),
                new UpdateOrganizationRequest("新名", "新描述"));
        assertEquals("新名", updated.name());
        assertEquals(ORG_PREFIX + "UPD", updated.code());
        assertEquals(ownerId, updated.ownerId());
    }

    @Test
    void deleteByNonOwnerShouldThrow403() {
        Long ownerId = createTestUser("delown");
        Long otherId = createTestUser("delother");
        OrganizationVO org = createTestOrg("DEL", ownerId);
        assertThrows(ForbiddenException.class, () -> organizationService.delete(org.id(), otherId));
    }

    @Test
    void deleteByOwnerShouldCascadeMembersAndDepartments() {
        Long ownerId = createTestUser("delown2");
        OrganizationVO org = createTestOrg("DEL2", ownerId);
        Long deptId = departmentService.create(org.id(),
                new CreateDepartmentRequest("研发部", "RD", null, null)).id();
        organizationService.delete(org.id(), ownerId);
        assertThrows(ResourceNotFoundException.class, () -> organizationService.getById(org.id()));
        assertTrue(departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getOrgId, org.id())).isEmpty(), "部门应级联删除");
        assertThrows(ResourceNotFoundException.class, () -> departmentService.getById(deptId));
    }

    // ===== 成员管理 =====

    @Test
    void addMemberShouldRequireExistingUserAndNoDuplicate() {
        Long ownerId = createTestUser("mown");
        Long memberId = createTestUser("mmember");
        OrganizationVO org = createTestOrg("MEM", ownerId);

        OrganizationMemberVO member = organizationService.addMember(org.id(),
                new AddOrganizationMemberRequest(memberId, "MEMBER", null));
        assertEquals(OrgMemberType.MEMBER, member.role());

        // 重复添加 409
        BusinessException ex = assertThrows(BusinessException.class, () -> organizationService.addMember(
                org.id(), new AddOrganizationMemberRequest(memberId, "MEMBER", null)));
        assertEquals(409, ex.getStatus());

        // 未知用户 404
        assertThrows(ResourceNotFoundException.class, () -> organizationService.addMember(org.id(),
                new AddOrganizationMemberRequest(999999999L, "MEMBER", null)));
    }

    @Test
    void removeOwnerShouldBeRejectedButMemberRemovable() {
        Long ownerId = createTestUser("rown");
        Long memberId = createTestUser("rmember");
        OrganizationVO org = createTestOrg("RM", ownerId);
        organizationService.addMember(org.id(), new AddOrganizationMemberRequest(memberId, "ADMIN", null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> organizationService.removeMember(org.id(), ownerId));
        assertEquals(400, ex.getStatus());

        organizationService.removeMember(org.id(), memberId);
        assertTrue(organizationService.listMembers(org.id()).stream()
                .allMatch(m -> m.role() == OrgMemberType.OWNER));
    }

    // ===== 部门树 =====

    @Test
    void departmentCreateWithForeignOrgParentShouldThrow400() {
        Long ownerId = createTestUser("dtrue");
        Long otherOwnerId = createTestUser("dfalse");
        OrganizationVO orgA = createTestOrg("DTA", ownerId);
        OrganizationVO orgB = createTestOrg("DTB", otherOwnerId);
        Long deptB = departmentService.create(orgB.id(),
                new CreateDepartmentRequest("B部门", "B", null, null)).id();
        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.create(
                orgA.id(), new CreateDepartmentRequest("A部门", "A", null, deptB)));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void departmentUpdateMustNotCreateCycle() {
        Long ownerId = createTestUser("cyc");
        OrganizationVO org = createTestOrg("CYC", ownerId);
        Long parent = departmentService.create(org.id(),
                new CreateDepartmentRequest("父部门", "P", null, null)).id();
        Long child = departmentService.create(org.id(),
                new CreateDepartmentRequest("子部门", "C", null, parent)).id();

        // 父部门挂到子部门下 → 成环，必须拒绝
        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(parent, "父部门", child));
        assertEquals(400, ex.getStatus());
        // 自引用同样拒绝
        assertThrows(BusinessException.class, () -> departmentService.update(parent, "父部门", parent));
    }

    @Test
    void deleteParentShouldPromoteChildAndNullMemberDepartment() {
        Long ownerId = createTestUser("delp");
        Long memberId = createTestUser("delm");
        OrganizationVO org = createTestOrg("DELP", ownerId);
        Long parent = departmentService.create(org.id(),
                new CreateDepartmentRequest("父", "P1", null, null)).id();
        Long child = departmentService.create(org.id(),
                new CreateDepartmentRequest("子", "C1", null, parent)).id();
        organizationService.addMember(org.id(), new AddOrganizationMemberRequest(memberId, "MEMBER", parent));

        departmentService.delete(parent);

        // 子部门提升为根
        Department childAfter = departmentMapper.selectById(child);
        assertNull(childAfter.getParentId(), "子部门应提升为根");
        // 成员 departmentId 置 NULL 但成员关系保留
        OrganizationMemberVO memberAfter = organizationService.listMembers(org.id()).stream()
                .filter(m -> m.userId().equals(memberId)).findFirst().orElseThrow();
        assertNull(memberAfter.departmentId());
        assertEquals(OrgMemberType.MEMBER, memberAfter.role());
    }

    @Test
    void userDeletionCascadesMembership() {
        Long ownerId = createTestUser("casown");
        Long tempUserId = createTestUser("castmp");
        OrganizationVO org = createTestOrg("CAS", ownerId);
        organizationService.addMember(org.id(), new AddOrganizationMemberRequest(tempUserId, "MEMBER", null));
        // 用户删除 → 成员关系级联清理（FK CASCADE），组织保留
        userMapper.deleteById(tempUserId);
        assertTrue(organizationService.listMembers(org.id()).stream()
                .noneMatch(m -> m.userId().equals(tempUserId)));
    }
}
