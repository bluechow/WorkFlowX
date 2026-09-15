package com.workflowx.org.controller;

import com.workflowx.common.exception.ValidationException;
import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.org.dto.AddOrganizationMemberRequest;
import com.workflowx.org.dto.CreateDepartmentRequest;
import com.workflowx.org.dto.CreateOrganizationRequest;
import com.workflowx.org.dto.UpdateOrganizationRequest;
import com.workflowx.org.service.DepartmentService;
import com.workflowx.org.service.OrganizationService;
import com.workflowx.org.vo.DepartmentVO;
import com.workflowx.org.vo.OrganizationMemberVO;
import com.workflowx.org.vo.OrganizationVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 组织架构接口（P4-02）。
 * 权限: hasAuthority（org 与 department 系列权限码，V5 种子）后端强制；org:delete 叠加数据级 OWNER 校验（403）。
 * 操作者身份取自 SecurityContext，不接受客户端传入。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OrgController {

    private final OrganizationService organizationService;
    private final DepartmentService departmentService;

    // ===== 组织 =====

    @GetMapping("/orgs")
    @PreAuthorize("hasAuthority('org:list')")
    public Result<PageVO<OrganizationVO>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return Result.ok(organizationService.page(keyword,
                page == null ? 1 : page, size == null ? 20 : size));
    }

    @PostMapping("/orgs")
    @PreAuthorize("hasAuthority('org:create')")
    public ResponseEntity<Result<OrganizationVO>> create(@Valid @RequestBody CreateOrganizationRequest request,
                                                         @AuthenticationPrincipal JwtPayload operator) {
        OrganizationVO created = organizationService.create(request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/orgs/{id}")
    @PreAuthorize("hasAuthority('org:get')")
    public Result<OrganizationVO> getById(@PathVariable Long id) {
        return Result.ok(organizationService.getById(id));
    }

    @PutMapping("/orgs/{id}")
    @PreAuthorize("hasAuthority('org:update')")
    public Result<OrganizationVO> update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateOrganizationRequest request) {
        return Result.ok(organizationService.update(id, request));
    }

    @DeleteMapping("/orgs/{id}")
    @PreAuthorize("hasAuthority('org:delete')")
    public Result<Void> delete(@PathVariable Long id, @AuthenticationPrincipal JwtPayload operator) {
        organizationService.delete(id, operator.userId());
        return Result.ok();
    }

    // ===== 组织成员 =====

    @GetMapping("/orgs/{id}/members")
    @PreAuthorize("hasAuthority('org:get')")
    public Result<List<OrganizationMemberVO>> listMembers(@PathVariable Long id) {
        return Result.ok(organizationService.listMembers(id));
    }

    @PostMapping("/orgs/{id}/members")
    @PreAuthorize("hasAuthority('org:assign_member')")
    public Result<OrganizationMemberVO> addMember(@PathVariable Long id,
                                                  @Valid @RequestBody AddOrganizationMemberRequest request) {
        return Result.ok(organizationService.addMember(id, request));
    }

    @DeleteMapping("/orgs/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('org:assign_member')")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        organizationService.removeMember(id, userId);
        return Result.ok();
    }

    // ===== 部门 =====

    @GetMapping("/orgs/{id}/departments")
    @PreAuthorize("hasAuthority('department:list')")
    public Result<List<DepartmentVO>> listDepartments(@PathVariable Long id) {
        return Result.ok(departmentService.listByOrg(id));
    }

    @PostMapping("/orgs/{id}/departments")
    @PreAuthorize("hasAuthority('department:create')")
    public Result<DepartmentVO> createDepartment(@PathVariable Long id,
                                                 @Valid @RequestBody CreateDepartmentRequest request) {
        return Result.ok(departmentService.create(id, request));
    }

    @GetMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('department:get')")
    public Result<DepartmentVO> getDepartment(@PathVariable Long id) {
        return Result.ok(departmentService.getById(id));
    }

    @PatchMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('department:update')")
    public Result<DepartmentVO> updateDepartment(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        Object parentRaw = body == null ? null : body.get("parentId");
        Long parentId;
        if (parentRaw instanceof Number number) {
            parentId = number.longValue();
        } else if (parentRaw == null) {
            parentId = null;
        } else {
            throw new ValidationException("parentId 必须为数字或 null");
        }
        return Result.ok(departmentService.update(id, name, parentId));
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('department:delete')")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok();
    }
}
