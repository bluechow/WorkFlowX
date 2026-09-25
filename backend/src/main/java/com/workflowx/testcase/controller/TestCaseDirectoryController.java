package com.workflowx.testcase.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.Result;
import com.workflowx.testcase.dto.CreateTestCaseDirectoryRequest;
import com.workflowx.testcase.dto.UpdateTestCaseDirectoryRequest;
import com.workflowx.testcase.service.TestCaseDirectoryService;
import com.workflowx.testcase.vo.DirectoryVO;
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

import java.util.List;

/**
 * 用例目录接口（Phase 20; ADR-022）。
 * 权限: testcase:* authority + 项目成员数据级（Service 层，读写均要求）。
 */
@Tag(name = "Test Case Directories", description = "测试用例目录（Phase 20，项目内部资产）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/testcase-directories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestCaseDirectoryController {

    private final TestCaseDirectoryService directoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('testcase:list')")
    public Result<List<DirectoryVO>> list(@PathVariable Long projectId,
                                          @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(directoryService.list(projectId, operator.userId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('testcase:create')")
    public ResponseEntity<Result<DirectoryVO>> create(@PathVariable Long projectId,
                                                      @Valid @RequestBody CreateTestCaseDirectoryRequest request,
                                                      @AuthenticationPrincipal JwtPayload operator) {
        DirectoryVO created = directoryService.create(projectId, request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @PutMapping("/{directoryId}")
    @PreAuthorize("hasAuthority('testcase:update')")
    public Result<DirectoryVO> update(@PathVariable Long projectId, @PathVariable Long directoryId,
                                      @Valid @RequestBody UpdateTestCaseDirectoryRequest request,
                                      @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(directoryService.update(projectId, directoryId, request, operator.userId()));
    }

    @DeleteMapping("/{directoryId}")
    @PreAuthorize("hasAuthority('testcase:delete')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long directoryId,
                               @AuthenticationPrincipal JwtPayload operator) {
        directoryService.delete(projectId, directoryId, operator.userId());
        return Result.ok(null);
    }
}
