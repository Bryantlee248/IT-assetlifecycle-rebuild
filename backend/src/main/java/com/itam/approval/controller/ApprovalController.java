package com.itam.approval.controller;

import com.itam.approval.application.ApprovalService;
import com.itam.approval.dto.ApprovalInstanceResponse;
import com.itam.approval.dto.ApprovalTaskResponse;
import com.itam.approval.dto.DecisionRequest;
import com.itam.approval.entity.InstanceStatus;
import com.itam.approval.entity.TaskStatus;
import com.itam.common.result.ApiResponse;
import com.itam.security.JwtUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 审批控制器。权限码：
 *   approval:view（待办/实例列表/详情）、approval:approve（通过）、approval:reject（驳回）。
 * 路径前缀不含 /api（由 server.servlet.context-path=/api 提供），完整路径 /api/v1/approvals。
 * tenant_id / userId / displayName 一律取自 JWT principal，前端不可伪造。
 */
@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/tasks/my")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('approval:view')")
    public ApiResponse<List<ApprovalTaskResponse>> myTasks(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                           @RequestParam(required = false) TaskStatus status) {
        return ApiResponse.success(
                approvalService.getMyTodos(principal.getTenantId(), principal.getUserId(), status));
    }

    @GetMapping("/instances")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('approval:view')")
    public ApiResponse<List<ApprovalInstanceResponse>> instances(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                                @RequestParam(required = false) InstanceStatus status) {
        return ApiResponse.success(approvalService.getInstances(principal.getTenantId(), status));
    }

    @GetMapping("/instances/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('approval:view')")
    public ApiResponse<ApprovalInstanceResponse> instance(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                          @PathVariable UUID id) {
        return ApiResponse.success(
                approvalService.getInstance(principal.getTenantId(), principal.getUserId(), id));
    }

    @PostMapping("/instances/{id}/approve")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('approval:approve')")
    public ApiResponse<ApprovalInstanceResponse> approve(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                         @PathVariable UUID id,
                                                         @RequestBody(required = false) DecisionRequest req) {
        String comment = (req == null) ? null : req.getComment();
        return ApiResponse.success(approvalService.approve(
                principal.getTenantId(), principal.getUserId(), principal.getDisplayName(), id, comment));
    }

    @PostMapping("/instances/{id}/reject")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('approval:reject')")
    public ApiResponse<ApprovalInstanceResponse> reject(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                        @PathVariable UUID id,
                                                        @RequestBody(required = false) DecisionRequest req) {
        String comment = (req == null) ? null : req.getComment();
        return ApiResponse.success(approvalService.reject(
                principal.getTenantId(), principal.getUserId(), principal.getDisplayName(), id, comment));
    }
}
