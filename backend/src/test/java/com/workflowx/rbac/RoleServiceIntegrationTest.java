package com.workflowx.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.rbac.dto.AssignRolePermissionsRequest;
import com.workflowx.rbac.dto.CreateRoleRequest;
import com.workflowx.rbac.dto.UpdateRoleRequest;
import com.workflowx.rbac.entity.Role;
import com.workflowx.rbac.mapper.RoleMapper;
import com.workflowx.rbac.service.RoleService;
import com.workflowx.rbac.service.UserRoleService;
import com.workflowx.rbac.vo.RoleVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoleService / UserRoleService 集成测试（P3-02）：真实 MySQL（compose :3307）。
 * 数据隔离: 测试角色统一 p3_test_ 前缀（非系统角色，可安全删除），用后清理。
 * 系统角色（ADMIN/MEMBER）行为通过 V3 种子实测验证。
 */
@SpringBootTest
class RoleServiceIntegrationTest {

    private static final String PREFIX = "p3_test_";

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleMapper roleMapper;

    @AfterEach
    void cleanup() {
        roleMapper.delete(new LambdaQueryWrapper<Role>().likeRight(Role::getCode, PREFIX));
    }

    private RoleVO createTestRole(String suffix) {
        return roleService.create(new CreateRoleRequest(
                PREFIX + suffix, "测试角色-" + suffix, "integration"));
    }

    // ===== 创建 =====

    @Test
    void createShouldPersistAndReturnVo() {
        RoleVO created = createTestRole("create");
        assertTrue(created.id() > 0);
        assertEquals(PREFIX + "create", created.code());
        assertFalse(created.system());
        Role persisted = roleMapper.selectById(created.id());
        assertEquals("测试角色-create", persisted.getName());
    }

    @Test
    void createDuplicateCodeShouldThrow409() {
        createTestRole("dup");
        BusinessException ex = assertThrows(BusinessException.class, () -> createTestRole("dup"));
        assertEquals(409, ex.getStatus());
    }

    // ===== 查询 =====

    @Test
    void getByIdMissingShouldThrow404() {
        assertThrows(ResourceNotFoundException.class, () -> roleService.getById(999999999L));
    }

    @Test
    void listShouldIncludeSystemRolesWithSystemFlag() {
        List<RoleVO> all = roleService.list();
        RoleVO admin = all.stream().filter(r -> "ADMIN".equals(r.code())).findFirst().orElseThrow();
        RoleVO member = all.stream().filter(r -> "MEMBER".equals(r.code())).findFirst().orElseThrow();
        assertTrue(admin.system());
        assertTrue(member.system());
    }

    // ===== 更新 =====

    @Test
    void updateShouldChangeNameAndDescriptionButNotCode() {
        RoleVO created = createTestRole("upd");
        RoleVO updated = roleService.update(created.id(),
                new UpdateRoleRequest("改名角色", "new-desc"));
        assertEquals("改名角色", updated.name());
        assertEquals("new-desc", updated.description());
        assertEquals(PREFIX + "upd", updated.code(), "code 不可被 update 修改");
    }

    // ===== 删除与系统角色保护 =====

    @Test
    void deleteNonSystemRoleShouldSucceed() {
        RoleVO created = createTestRole("del");
        roleService.delete(created.id());
        assertThrows(ResourceNotFoundException.class, () -> roleService.getById(created.id()));
    }

    @Test
    void deleteSystemRoleShouldThrow400() {
        Long adminId = roleService.list().stream()
                .filter(r -> "ADMIN".equals(r.code())).findFirst().orElseThrow().id();
        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.delete(adminId));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("ADMIN"));
    }

    // ===== 权限绑定（replace 语义） =====

    @Test
    void assignPermissionsShouldReplaceSetAtomically() {
        RoleVO created = createTestRole("bind");
        roleService.assignPermissions(created.id(),
                new AssignRolePermissionsRequest(Set.of("user:list", "user:get")));
        assertEquals(List.of("user:list", "user:get"), roleService.getPermissionCodes(created.id()));

        // 整体替换: user:get 被移除, role:list 新增
        roleService.assignPermissions(created.id(),
                new AssignRolePermissionsRequest(Set.of("user:list", "role:list")));
        List<String> after = roleService.getPermissionCodes(created.id());
        assertTrue(after.contains("user:list") && after.contains("role:list"));
        assertFalse(after.contains("user:get"));
    }

    @Test
    void assignPermissionsWithUnknownCodeShouldThrow404() {
        RoleVO created = createTestRole("bind404");
        assertThrows(ResourceNotFoundException.class, () -> roleService.assignPermissions(created.id(),
                new AssignRolePermissionsRequest(Set.of("no_such:permission"))));
    }

    @Test
    void assignEmptySetShouldClearPermissions() {
        RoleVO created = createTestRole("clear");
        roleService.assignPermissions(created.id(),
                new AssignRolePermissionsRequest(Set.of("user:list")));
        roleService.assignPermissions(created.id(), new AssignRolePermissionsRequest(Set.of()));
        assertTrue(roleService.getPermissionCodes(created.id()).isEmpty());
    }

    @Test
    void adminRoleShouldHaveAllSystemPermissionsFromV3Seed() {
        Long adminId = roleService.list().stream()
                .filter(r -> "ADMIN".equals(r.code())).findFirst().orElseThrow().id();
        List<String> codes = roleService.getPermissionCodes(adminId);
        assertEquals(RbacConstants.SYSTEM_PERMISSION_CODES, Set.copyOf(codes),
                "ADMIN 应绑定 V3 全部 14 项系统权限");
    }

    // ===== 用户-角色绑定 =====

    @Test
    void assignAndRevokeRoleShouldBeIdempotent() {
        // user1 为 dev seed MEMBER 用户；绑定 p3_test_ 角色后回收（不污染 seed 的 MEMBER 绑定）
        Long user1Id = 2L;
        createTestRole("assign");
        userRoleService.assignRole(user1Id, PREFIX + "assign");
        userRoleService.assignRole(user1Id, PREFIX + "assign");
        assertTrue(userRoleService.findRoleCodesByUserId(user1Id).contains(PREFIX + "assign"));

        userRoleService.revokeRole(user1Id, PREFIX + "assign");
        userRoleService.revokeRole(user1Id, PREFIX + "assign");
        assertFalse(userRoleService.findRoleCodesByUserId(user1Id).contains(PREFIX + "assign"));
        // seed 绑定不受影响
        assertTrue(userRoleService.findRoleCodesByUserId(user1Id).contains("MEMBER"));
    }

    @Test
    void assignUnknownRoleShouldThrow404() {
        assertThrows(ResourceNotFoundException.class,
                () -> userRoleService.assignRole(2L, "no_such_role_p3"));
    }

    @Test
    void findRolesByUserIdShouldReturnMemberForSeedUser() {
        List<RoleVO> roles = userRoleService.findRolesByUserId(2L);
        assertTrue(roles.stream().anyMatch(r -> "MEMBER".equals(r.code()) && r.system()));
    }
}
