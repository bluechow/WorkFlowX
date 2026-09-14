package com.workflowx.user.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.rbac.dto.CreateRoleRequest;
import com.workflowx.rbac.dto.UpdateRoleRequest;
import com.workflowx.rbac.service.PermissionService;
import com.workflowx.rbac.service.RoleService;
import com.workflowx.rbac.service.UserRoleService;
import com.workflowx.rbac.vo.PermissionVO;
import com.workflowx.rbac.vo.RoleVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RBAC 管理接口（P3-03）。
 * 权限模型: 细粒度 authority（{resource}:{action}）后端强制——authorities 由
 * JwtAuthenticationFilter 实时解析（user→role→permission），收权即时生效（ADR-012）。
 * 系统角色/权限保护在 Service 层（400）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RbacController {

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;

    // ===== 角色 =====

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:list')")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleService.list());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:create')")
    public ResponseEntity<Result<RoleVO>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleVO created = roleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:get')")
    public Result<RoleVO> getRole(@PathVariable Long id) {
        return Result.ok(roleService.getById(id));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:update')")
    public Result<RoleVO> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.ok(roleService.update(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    // ===== 角色权限绑定 =====

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:get')")
    public Result<List<String>> getRolePermissions(@PathVariable Long id) {
        return Result.ok(roleService.getPermissionCodes(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:assign_permission')")
    public Result<List<String>> assignRolePermissions(@PathVariable Long id,
                                                      @Valid @RequestBody com.workflowx.rbac.dto.AssignRolePermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return Result.ok(roleService.getPermissionCodes(id));
    }

    // ===== 权限 =====

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission:list')")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(permissionService.list());
    }

    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('permission:get')")
    public Result<PermissionVO> getPermission(@PathVariable Long id) {
        return Result.ok(permissionService.getById(id));
    }

    // ===== 用户角色绑定 =====

    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:get')")
    public Result<List<String>> getUserRoles(@PathVariable Long id) {
        return Result.ok(userRoleService.findRoleCodesByUserId(id));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign_role')")
    public Result<List<String>> replaceUserRoles(@PathVariable Long id,
                                                 @Valid @RequestBody com.workflowx.rbac.dto.AssignUserRolesRequest request) {
        userRoleService.replaceUserRoles(id, request.roleCodes());
        return Result.ok(userRoleService.findRoleCodesByUserId(id));
    }

    @PostMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign_role')")
    public Result<List<String>> assignUserRole(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        String roleCode = body == null ? null : body.get("roleCode");
        if (roleCode == null || roleCode.isBlank()) {
            throw new com.workflowx.common.exception.ValidationException("roleCode 不能为空");
        }
        userRoleService.assignRole(id, roleCode);
        return Result.ok(userRoleService.findRoleCodesByUserId(id));
    }

    @DeleteMapping("/users/{id}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('user:assign_role')")
    public Result<List<String>> revokeUserRole(@PathVariable Long id, @PathVariable String roleCode) {
        userRoleService.revokeRole(id, roleCode);
        return Result.ok(userRoleService.findRoleCodesByUserId(id));
    }
}
