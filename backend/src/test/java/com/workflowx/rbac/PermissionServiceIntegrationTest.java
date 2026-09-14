package com.workflowx.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflowx.common.exception.BusinessException;
import com.workflowx.common.exception.ResourceNotFoundException;
import com.workflowx.rbac.dto.CreatePermissionRequest;
import com.workflowx.rbac.entity.Permission;
import com.workflowx.rbac.entity.PermissionType;
import com.workflowx.rbac.mapper.PermissionMapper;
import com.workflowx.rbac.service.PermissionService;
import com.workflowx.rbac.vo.PermissionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PermissionService 集成测试（P3-02）：真实 MySQL。
 * 数据隔离: 测试权限统一 p3_test: 前缀（合法 {resource}:{action} 编码），用后清理。
 */
@SpringBootTest
class PermissionServiceIntegrationTest {

    private static final String PREFIX = "p3_test:";

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionMapper permissionMapper;

    @AfterEach
    void cleanup() {
        permissionMapper.delete(new LambdaQueryWrapper<Permission>().likeRight(Permission::getCode, PREFIX));
    }

    private PermissionVO createTestPermission(String suffix) {
        return permissionService.create(new CreatePermissionRequest(
                PREFIX + suffix, "测试权限-" + suffix, "integration"));
    }

    @Test
    void createShouldDefaultToApiType() {
        PermissionVO created = createTestPermission("create");
        assertEquals(PermissionType.API, created.type());
        assertFalse(created.system());
    }

    @Test
    void createDuplicateCodeShouldThrow409() {
        createTestPermission("dup");
        BusinessException ex = assertThrows(BusinessException.class, () -> createTestPermission("dup"));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void getByCodeShouldReturnSystemPermission() {
        PermissionVO userList = permissionService.getByCode("user:list");
        assertEquals("user:list", userList.code());
        assertTrue(userList.system(), "V3 种子权限应标记为 system");
        assertEquals(PermissionType.API, userList.type());
    }

    @Test
    void getByUnknownCodeShouldThrow404() {
        assertThrows(ResourceNotFoundException.class, () -> permissionService.getByCode("no_such:perm"));
    }

    @Test
    void listShouldContainAllV3SystemPermissions() {
        List<String> codes = permissionService.list().stream().map(PermissionVO::code).toList();
        assertTrue(codes.containsAll(RbacConstants.SYSTEM_PERMISSION_CODES),
                "权限列表应包含 V3 全部系统权限");
    }

    @Test
    void deleteSystemPermissionShouldThrow400() {
        Long id = permissionService.getByCode("user:status").id();
        BusinessException ex = assertThrows(BusinessException.class, () -> permissionService.delete(id));
        assertEquals(400, ex.getStatus());
        // 确认未被删除
        assertEquals("user:status", permissionService.getById(id).code());
    }

    @Test
    void deleteNonSystemPermissionShouldSucceed() {
        PermissionVO created = createTestPermission("del");
        permissionService.delete(created.id());
        assertThrows(ResourceNotFoundException.class, () -> permissionService.getById(created.id()));
    }

    @Test
    void memberRoleShouldHaveNoPermissionsByDesign() {
        // 设计决策（ADR-012）: MEMBER 暂无管理权限
        List<String> codes = permissionService.findPermissionCodesByUserId(2L);
        assertFalse(codes.contains("user:list"), "MEMBER 不应持有管理权限");
    }

    @Test
    void adminUserShouldResolveAllSystemPermissionsViaJoin() {
        // admin（userId=1, ADMIN 角色）→ 三表 JOIN 实时解析
        List<String> codes = permissionService.findPermissionCodesByUserId(1L);
        assertEquals(RbacConstants.SYSTEM_PERMISSION_CODES, Set8.copyOf(codes));
    }

    /** 小工具: List → Set 断言（避免额外 import 噪音） */
    private static final class Set8 {
        private static java.util.Set<String> copyOf(List<String> list) {
            return new java.util.HashSet<>(list);
        }
    }
}
