package com.itam.notification.controller;

import com.itam.common.result.ApiResponse;
import com.itam.notification.application.NotificationService;
import com.itam.notification.dto.NotificationResponse;
import com.itam.notification.entity.NotificationType;
import com.itam.security.JwtUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 通知控制器。权限码：notification:view（列表/未读数）、notification:read（标记已读）。
 * 路径前缀不含 /api（由 server.servlet.context-path=/api 提供），完整路径 /api/v1/notifications。
 * tenant_id / userId 一律取自 JWT principal，且查询/更新服务端强校验 receiver_id 防越权。
 */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('notification:view')")
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                        @RequestParam(required = false) NotificationType type) {
        return ApiResponse.success(
                notificationService.list(principal.getTenantId(), principal.getUserId(), type));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('notification:view')")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(notificationService.unreadCount(principal.getTenantId(), principal.getUserId()));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('notification:read')")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal JwtUserPrincipal principal,
                                      @PathVariable UUID id) {
        notificationService.markRead(principal.getTenantId(), principal.getUserId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('notification:read')")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal JwtUserPrincipal principal) {
        notificationService.markAllRead(principal.getTenantId(), principal.getUserId());
        return ApiResponse.success(null);
    }
}
