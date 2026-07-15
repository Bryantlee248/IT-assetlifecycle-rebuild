package com.itam.lifecycle.controller;

import com.itam.common.result.ApiResponse;
import com.itam.lifecycle.application.LifecycleAppService;
import com.itam.lifecycle.dto.ExecuteActionRequest;
import com.itam.lifecycle.dto.LifecycleActionResponse;
import com.itam.lifecycle.dto.LifecycleActionResult;
import com.itam.lifecycle.dto.LifecycleEventResponse;
import com.itam.lifecycle.dto.LifecycleStatusResponse;
import com.itam.security.JwtUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 生命周期控制器：4 个端点。
 * 鉴权：GET（status/events/actions）-> lifecycle:view；POST action -> lifecycle:transition。
 * 路径前缀不含 /api（由 server.servlet.context-path=/api 提供），完整路径
 *   /api/v1/assets/{assetId}/lifecycle[/...]。
 * tenant_id / userId / displayName 均来自 JWT principal，前端不可伪造。
 */
@RestController
@RequestMapping("/v1/assets/{assetId}/lifecycle")
public class LifecycleController {

    private final LifecycleAppService lifecycleAppService;

    public LifecycleController(LifecycleAppService lifecycleAppService) {
        this.lifecycleAppService = lifecycleAppService;
    }

    @GetMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('lifecycle:view')")
    public ApiResponse<LifecycleStatusResponse> getStatus(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                          @PathVariable UUID assetId) {
        return ApiResponse.success(lifecycleAppService.getStatus(principal.getTenantId(), assetId));
    }

    @GetMapping("/events")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('lifecycle:view')")
    public ApiResponse<List<LifecycleEventResponse>> getEvents(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                               @PathVariable UUID assetId) {
        return ApiResponse.success(lifecycleAppService.getEvents(principal.getTenantId(), assetId));
    }

    @GetMapping("/actions")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('lifecycle:view')")
    public ApiResponse<List<LifecycleActionResponse>> getActions(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                                 @PathVariable UUID assetId) {
        return ApiResponse.success(lifecycleAppService.getActions(principal.getTenantId(), assetId));
    }

    @PostMapping("/actions/{actionCode}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('lifecycle:transition')")
    public ApiResponse<LifecycleActionResult> executeAction(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                            @PathVariable UUID assetId,
                                                            @PathVariable String actionCode,
                                                            @RequestBody ExecuteActionRequest req) {
        return ApiResponse.success(lifecycleAppService.executeAction(
                principal.getTenantId(), principal.getUserId(), principal.getDisplayName(),
                assetId, actionCode, req));
    }
}
