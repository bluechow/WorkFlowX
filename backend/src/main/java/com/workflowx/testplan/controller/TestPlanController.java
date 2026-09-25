package com.workflowx.testplan.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.testplan.dto.AddTestPlanItemsRequest;
import com.workflowx.testplan.dto.CreateTestPlanRequest;
import com.workflowx.testplan.dto.ExecuteItemRequest;
import com.workflowx.testplan.dto.UpdateTestPlanRequest;
import com.workflowx.testplan.service.TestPlanService;
import com.workflowx.testplan.vo.TestPlanItemVO;
import com.workflowx.testplan.vo.TestPlanVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试计划接口（V1.1; ADR-023）。嵌套于项目路径。
 * 权限: testplan:* authority + 项目成员数据级（读写均要求，成员校验先于目标查找）。
 */
@Tag(name = "Test Plans", description = "测试计划与执行（V1.1）")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/testplans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestPlanController {

    private final TestPlanService testPlanService;

    @GetMapping
    @PreAuthorize("hasAuthority('testplan:list')")
    public Result<PageVO<TestPlanVO>> page(@PathVariable Long projectId,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.page(projectId, page, size, operator.userId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('testplan:create')")
    public ResponseEntity<Result<TestPlanVO>> create(@PathVariable Long projectId,
                                                     @Valid @RequestBody CreateTestPlanRequest request,
                                                     @AuthenticationPrincipal JwtPayload operator) {
        TestPlanVO created = testPlanService.create(projectId, request, operator.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.of(201, "created", created));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAuthority('testplan:get')")
    public Result<TestPlanVO> get(@PathVariable Long projectId, @PathVariable Long planId,
                                  @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.get(projectId, planId, operator.userId()));
    }

    @GetMapping("/{planId}/items")
    @PreAuthorize("hasAuthority('testplan:get')")
    public Result<List<TestPlanItemVO>> items(@PathVariable Long projectId, @PathVariable Long planId,
                                              @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.items(projectId, planId, operator.userId()));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAuthority('testplan:update')")
    public Result<TestPlanVO> update(@PathVariable Long projectId, @PathVariable Long planId,
                                     @Valid @RequestBody UpdateTestPlanRequest request,
                                     @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.update(projectId, planId, request, operator.userId()));
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasAuthority('testplan:delete')")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long planId,
                               @AuthenticationPrincipal JwtPayload operator) {
        testPlanService.delete(projectId, planId, operator.userId());
        return Result.ok(null);
    }

    @PostMapping("/{planId}/items")
    @PreAuthorize("hasAuthority('testplan:update')")
    public Result<Integer> addItems(@PathVariable Long projectId, @PathVariable Long planId,
                                    @Valid @RequestBody AddTestPlanItemsRequest request,
                                    @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.addItems(projectId, planId, request, operator.userId()));
    }

    @DeleteMapping("/{planId}/items/{itemId}")
    @PreAuthorize("hasAuthority('testplan:update')")
    public Result<Void> removeItem(@PathVariable Long projectId, @PathVariable Long planId,
                                   @PathVariable Long itemId,
                                   @AuthenticationPrincipal JwtPayload operator) {
        testPlanService.removeItem(projectId, planId, itemId, operator.userId());
        return Result.ok(null);
    }

    @PutMapping("/{planId}/items/{itemId}/execute")
    @PreAuthorize("hasAuthority('testplan:update')")
    public Result<TestPlanItemVO> execute(@PathVariable Long projectId, @PathVariable Long planId,
                                          @PathVariable Long itemId,
                                          @Valid @RequestBody ExecuteItemRequest request,
                                          @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(testPlanService.execute(projectId, planId, itemId, request, operator.userId()));
    }
}
