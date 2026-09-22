package com.workflowx.notification.controller;

import com.workflowx.common.security.JwtPayload;
import com.workflowx.common.web.PageVO;
import com.workflowx.common.web.Result;
import com.workflowx.notification.dto.NotificationPageQuery;
import com.workflowx.notification.service.NotificationService;
import com.workflowx.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口（P9-05; ADR-019）。
 * self 资源: 无 notification:* authority（对齐 /auth/me 先例），仅要求认证；
 * 数据隔离由 Service 层 recipient ownership 强制（不只依赖 Controller），跨用户访问 404。
 */
@Tag(name = "Notifications", description = "站内通知（P9，self 资源）")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "我的通知列表（read 筛选 + 分页，created_at DESC）")
    public Result<PageVO<NotificationVO>> list(@Valid NotificationPageQuery query,
                                               @AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(notificationService.listMy(operator.userId(), query.read(),
                query.pageNum(), query.pageSize()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "我的未读通知数量")
    public Result<Map<String, Long>> unreadCount(@AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(Map.of("count", notificationService.unreadCount(operator.userId())));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "标记单条已读（幂等；非本人通知 404）")
    public Result<Void> markRead(@PathVariable Long id,
                                 @AuthenticationPrincipal JwtPayload operator) {
        notificationService.markRead(operator.userId(), id);
        return Result.ok(null);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "全部已读（仅当前用户未读）")
    public Result<Map<String, Integer>> markAllRead(@AuthenticationPrincipal JwtPayload operator) {
        return Result.ok(Map.of("updated", notificationService.markAllRead(operator.userId())));
    }
}
