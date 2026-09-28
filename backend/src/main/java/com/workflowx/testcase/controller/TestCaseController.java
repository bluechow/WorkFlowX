package com.workflowx.testcase.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.testcase.dto.CreateTestCaseRequest;
import com.workflowx.testcase.dto.TestCasePageQuery;
import com.workflowx.testcase.dto.UpdateTestCaseRequest;
import com.workflowx.testcase.service.TestCaseService;
import com.workflowx.testcase.vo.TestCaseVO;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用例接口（Phase 20; ADR-022）。嵌套于项目路径，跨项目访问在 Service 层防为 404/403。
 * 权限: testcase:* authority + 项目成员数据级（读写均要求，成员校验先于目标查找）。
 */
@Tag(name = "Test Cases", description = "测试用例（Phase 20，项目内部资产）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/testcases")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final com.workflowx.testcase.service.TestCaseExcelService excelService;

    @GetMapping
    @PreAuthorize("hasAuthority('testcase:list')")
    public Result<PageVO<TestCaseVO>> page(@PathVariable Long projectId,
                                           @Valid TestCasePageQuery query,
                                           @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testCaseService.page(projectId, query, operator.userId()));
    }

    /** 导出全部用例为 xlsx（Phase A-⑦；读语义对齐 list，testcase:list） */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('testcase:list')")
    public org.springframework.http.ResponseEntity<byte[]> export(@PathVariable Long projectId,
                                                                  @AuthenticationPrincipal JwtPayload operator) {
        byte[] bytes = excelService.exportProjectCases(projectId, operator.userId());
        String fileName = java.net.URLEncoder.encode("用例库.xlsx", java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + fileName)
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(bytes);
    }

    /** 导入用例（Phase A-⑦）：testcase:create + 项目成员（服务层校验）；行级错误逐行返回 */
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('testcase:create')")
    public Result<com.workflowx.testcase.dto.TestCaseImportResultVO> importCases(
            @PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(excelService.importProjectCases(projectId, file, operator.userId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('testcase:create')")
    public ResponseEntity<Result<TestCaseVO>> create(@PathVariable Long projectId,
                                                     @Valid @RequestBody CreateTestCaseRequest request,
                                                     @AuthenticationPrincipal JwtPayload operator) {
        TestCaseVO created = testCaseService.create(projectId, request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/{testcaseId}")
    @PreAuthorize("hasAuthority('testcase:get')")
    public Result<TestCaseVO> getById(@PathVariable Long projectId, @PathVariable Long testcaseId,
                                      @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testCaseService.getById(projectId, testcaseId, operator.userId()));
    }

    @PutMapping("/{testcaseId}")
    @PreAuthorize("hasAuthority('testcase:update')")
    public Result<TestCaseVO> update(@PathVariable Long projectId, @PathVariable Long testcaseId,
                                     @Valid @RequestBody UpdateTestCaseRequest request,
                                     @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testCaseService.update(projectId, testcaseId, request, operator.userId()));
    }

    @DeleteMapping("/{testcaseId}")
    @PreAuthorize("hasAuthority('testcase:delete')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long testcaseId,
                               @AuthenticationPrincipal JwtPayload operator) {
        testCaseService.delete(projectId, testcaseId, operator.userId());
        return Result.ok(null);
    }
}
