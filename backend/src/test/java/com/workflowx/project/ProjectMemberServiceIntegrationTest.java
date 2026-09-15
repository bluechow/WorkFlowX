package com.workflowx.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ForbiddenException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.entity.Organization;
import com.workflowx.org.mapper.OrganizationMapper;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.project.dto.AddProjectMemberRequest;
import com.workflowx.project.dto.CreateProjectRequest;
import com.workflowx.project.entity.Project;
import com.workflowx.project.entity.ProjectMember;
import com.workflowx.project.entity.ProjectMemberRole;
import com.workflowx.project.mapper.ProjectMapper;
import com.workflowx.project.mapper.ProjectMemberMapper;
import com.workflowx.project.service.ProjectMemberService;
import com.workflowx.project.service.ProjectService;
import com.workflowx.project.vo.ProjectMemberVO;
import com.workflowx.project.vo.ProjectVO;
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
 * 项目成员领域集成测试（P5-04）：真实 MySQL。
 * 数据隔离: 项目 P5MEM 前缀 key、组织 ORG_P5MEM_*、用户 p5mem_ 前缀，用后清理。
 */
@SpringBootTest
class ProjectMemberServiceIntegrationTest {

    private static final String KEY_PREFIX = "P5MEM";
    private static final String ORG_PREFIX = "ORG_P5MEM_";
    private static final String USER_PREFIX = "p5mem_";

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

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
                USER_PREFIX + suffix, USER_PREFIX + suffix + "@test.local", "MemPass@123", "mem-" + suffix));
        createdUserIds.add(created.id());
        return created.id();
    }

    /** 建组织(owner)+建项目(OWNER 成员自动写入)；返回 ownerId */
    private Long createOrgAndProject(String suffix) {
        Long ownerId = createTestUser("owner" + suffix);
        organizationService.create(new CreateOrganizationRequest(
                ORG_PREFIX + suffix, ORG_PREFIX + suffix, null), ownerId);
        projectService.create(new CreateProjectRequest(
                "成员项目-" + suffix, KEY_PREFIX + suffix, orgIdFor(suffix), "d"), ownerId);
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

    @Test
    void projectCreationShouldAutoCreateOwnerMembership() {
        Long ownerId = createOrgAndProject("AUTO");
        List<ProjectMemberVO> members = projectMemberService.listMembers(projectIdFor("AUTO"));
        assertEquals(1, members.size());
        assertEquals(ProjectMemberRole.OWNER, members.get(0).role());
        assertEquals(ownerId, members.get(0).userId());
    }

    @Test
    void addMemberShouldRequireTargetUserInOrg() {
        // 操作者=OWNER（合法）；被添加用户非组织成员 → 400（ADR-015 前置规则）
        Long ownerId = createOrgAndProject("PRE");
        Long projectId = projectIdFor("PRE");
        Long outsider = createTestUser("outsider");

        BusinessException ex = assertThrows(BusinessException.class, () -> projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(outsider, "MEMBER"), ownerId));
        assertEquals(400, ex.getStatus(), "非组织成员应被拒绝（前置规则）");

        // outsider 加入组织后，由 OWNER 添加成功
        organizationService.addMember(orgIdFor("PRE"),
                new AddOrganizationMemberRequest(outsider, "MEMBER", null));
        ProjectMemberVO added = projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(outsider, "MEMBER"), ownerId);
        assertEquals(ProjectMemberRole.MEMBER, added.role());
    }

    @Test
    void nonMemberOperatorShouldBeRejected403BeforeTargetValidation() {
        // 操作者非组织成员 → 403 先于被添加用户校验（ADR-014 数据级）
        Long ownerId = createOrgAndProject("OP403B");
        Long projectId = projectIdFor("OP403B");
        Long outsider = createTestUser("out403b");
        Long target = createTestUser("tgt403b");
        organizationService.addMember(orgIdFor("OP403B"),
                new AddOrganizationMemberRequest(target, "MEMBER", null));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(target, "MEMBER"), outsider));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void addDuplicateMemberShouldThrow409() {
        Long ownerId = createOrgAndProject("DUP");
        Long projectId = projectIdFor("DUP");
        Long member = createTestUser("dupm");
        organizationService.addMember(orgIdFor("DUP"),
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(member, "MEMBER"), ownerId);

        BusinessException ex = assertThrows(BusinessException.class, () -> projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(member, "MEMBER"), ownerId));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void addUnknownUserShouldThrow404() {
        Long ownerId = createOrgAndProject("U404");
        assertThrows(ResourceNotFoundException.class, () -> projectMemberService.addMember(
                projectIdFor("U404"), new AddProjectMemberRequest(999999999L, "MEMBER"), ownerId));
    }

    @Test
    void ownerRoleShouldBeRejectedOnAdd() {
        Long ownerId = createOrgAndProject("OROLE");
        Long projectId = projectIdFor("OROLE");
        Long member = createTestUser("orole");
        organizationService.addMember(orgIdFor("OROLE"),
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        // Service 层显式拒绝 OWNER（HTTP 层由 DTO 正则 422 拒绝，双层守护）
        BusinessException ex = assertThrows(BusinessException.class, () -> projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(member, "OWNER"), ownerId));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void removeMemberShouldWorkButProtectOwner() {
        Long ownerId = createOrgAndProject("RM");
        Long projectId = projectIdFor("RM");
        Long member = createTestUser("rmm");
        organizationService.addMember(orgIdFor("RM"),
                new AddOrganizationMemberRequest(member, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(member, "MANAGER"), ownerId);

        // OWNER 移除 → 400
        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectMemberService.removeMember(projectId, ownerId, ownerId));
        assertEquals(400, ex.getStatus());

        // 普通成员移除成功；重复移除 404
        projectMemberService.removeMember(projectId, member, ownerId);
        assertThrows(ResourceNotFoundException.class,
                () -> projectMemberService.removeMember(projectId, member, ownerId));
    }

    @Test
    void nonOrgMemberOperatorShouldThrow403() {
        createOrgAndProject("OP403");
        Long projectId = projectIdFor("OP403");
        Long outsider = createTestUser("out403");
        Long target = createTestUser("tgt403");
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> projectMemberService.addMember(
                projectId, new AddProjectMemberRequest(target, "MEMBER"), outsider));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void userDeletionShouldCascadeProjectMembership() {
        Long ownerId = createOrgAndProject("CASC");
        Long projectId = projectIdFor("CASC");
        Long temp = createTestUser("cascu");
        organizationService.addMember(orgIdFor("CASC"),
                new AddOrganizationMemberRequest(temp, "MEMBER", null));
        projectMemberService.addMember(projectId, new AddProjectMemberRequest(temp, "MEMBER"), ownerId);

        userMapper.deleteById(temp);
        List<ProjectMember> after = projectMemberMapper.findMembersByProjectId(projectId);
        assertTrue(after.stream().noneMatch(m -> m.getUserId().equals(temp)));
    }
}
