package com.workflowx.rbac;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RBAC DTO Bean Validation 测试（P3-02）：编码规范边界。 */
class RbacDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private boolean validRole(Object request) {
        return validator.validate(request).isEmpty();
    }

    @Test
    void roleCodeBoundaries() {
        assertTrue(validRole(new com.workflowx.rbac.dto.CreateRoleRequest("AB", "n", null)), "2 位应通过");
        assertTrue(validRole(new com.workflowx.rbac.dto.CreateRoleRequest("A".repeat(50), "n", null)), "50 位应通过");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreateRoleRequest("A", "n", null)), "1 位应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreateRoleRequest("aBC", "n", null)), "小写应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreateRoleRequest("1BC", "n", null)), "数字开头应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreateRoleRequest("R-OLE", "n", null)), "连字符应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreateRoleRequest(null, "n", null)), "null 应失败");
    }

    @Test
    void roleUpdateMustNotContainCode() {
        // UpdateRoleRequest 类型上无 code 字段（类型系统保证），此处守护字段集合
        var fields = java.util.Arrays.stream(com.workflowx.rbac.dto.UpdateRoleRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(fields.equals(java.util.List.of("name", "description")), "更新 DTO 只允许 name/description: " + fields);
    }

    @Test
    void permissionCodeBoundaries() {
        assertTrue(validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("user:list", "n", null)), "标准编码应通过");
        assertTrue(validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("user:assign_role", "n", null)), "下划线 action 应通过");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("userlist", "n", null)), "无冒号应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("user:a:b", "n", null)), "三段应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("User:List", "n", null)), "大写应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreatePermissionRequest(":list", "n", null)), "空 resource 应失败");
        assertTrue(!validRole(new com.workflowx.rbac.dto.CreatePermissionRequest("user:", "n", null)), "空 action 应失败");
    }

    @Test
    void assignRolePermissionsShouldNormalizeNullAndValidateElements() {
        // null → 归一化为空集合（合法 = 清空语义）
        var normalized = new com.workflowx.rbac.dto.AssignRolePermissionsRequest(null);
        assertTrue(normalized.permissionCodes().isEmpty());
        // 集合内非法元素应被校验拒绝
        var invalid = new com.workflowx.rbac.dto.AssignRolePermissionsRequest(Set.of("BAD_CODE"));
        assertTrue(!validator.validate(invalid).isEmpty(), "非法元素编码应校验失败");
        var valid = new com.workflowx.rbac.dto.AssignRolePermissionsRequest(Set.of("user:list"));
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void systemConstantsShouldMatchV3V5SeedScope() {
        assertEquals(2, RbacConstants.SYSTEM_ROLE_CODES.size());
        assertEquals(30, RbacConstants.SYSTEM_PERMISSION_CODES.size(), "V3(14) + V5(11) + V7(5) = 30");
        assertTrue(RbacConstants.isSystemRole("ADMIN") && RbacConstants.isSystemRole("MEMBER"));
        assertTrue(!RbacConstants.isSystemRole("GUEST"));
        assertTrue(RbacConstants.isSystemPermission("user:create"));
        assertTrue(RbacConstants.isSystemPermission("org:create"));
        assertTrue(RbacConstants.isSystemPermission("department:delete"));
        assertTrue(RbacConstants.isSystemPermission("project:create"));
        assertTrue(!RbacConstants.isSystemPermission("p3_test:x"));
    }
}
