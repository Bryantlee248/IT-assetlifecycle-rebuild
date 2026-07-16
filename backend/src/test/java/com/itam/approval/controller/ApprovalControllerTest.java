package com.itam.approval.controller;

import com.itam.approval.application.ApprovalService;
import com.itam.approval.dto.ApprovalInstanceResponse;
import com.itam.approval.dto.ApprovalTaskResponse;
import com.itam.approval.entity.InstanceStatus;
import com.itam.approval.entity.TaskStatus;
import com.itam.security.JwtFilter;
import com.itam.security.JwtUserPrincipal;
import com.itam.security.JwtUtil;
import com.itam.security.MustChangePasswordFilter;
import com.itam.security.RefreshTokenStore;
import com.itam.security.RestAccessDeniedHandler;
import com.itam.security.RestAuthenticationEntryPoint;
import com.itam.security.SecurityConfig;
import com.itam.security.UserType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApprovalController 接口层测试（@WebMvcTest）：聚焦权限码门禁。
 * 权限码：approval:view（待办/实例/详情）、approval:approve（通过）、approval:reject（驳回）。
 * 鉴权门禁模式完整复制 LifecycleControllerTest。
 */
@WebMvcTest(controllers = ApprovalController.class)
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private RefreshTokenStore refreshTokenStore;
    @MockBean
    private RestAuthenticationEntryPoint entryPoint;
    @MockBean
    private RestAccessDeniedHandler accessDeniedHandler;
    @MockBean
    private ApprovalService approvalService;

    private final UUID tenantId = UUID.randomUUID();

    private JwtUserPrincipal principal(Set<String> perms) {
        return new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantId, UUID.randomUUID(),
                Set.of("asset_admin"), perms, false);
    }

    @Test
    void unauthenticated_myTasks_requiresAuth() throws Exception {
        doAnswer(inv -> {
            HttpServletResponse response = inv.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(entryPoint).commence(any(), any(), any());

        mockMvc.perform(get("/api/v1/approvals/tasks/my").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void without_approval_view_myTasks_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/approvals/tasks/my").contextPath("/api")
                        .with(user(principal(Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void with_approval_view_myTasks_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvalService.getMyTodos(any(), any(), any())).thenReturn(
                List.of(ApprovalTaskResponse.builder().id(id).instanceId(id).build()));

        mockMvc.perform(get("/api/v1/approvals/tasks/my").contextPath("/api")
                        .with(user(principal(Set.of("approval:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void with_approval_view_getInstance_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvalService.getInstance(any(), any(), eq(id))).thenReturn(
                ApprovalInstanceResponse.builder().id(id).status(InstanceStatus.PENDING).build());

        mockMvc.perform(get("/api/v1/approvals/instances/{id}", id).contextPath("/api")
                        .with(user(principal(Set.of("approval:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void approve_without_approve_perm_forbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/approvals/instances/{id}/approve", id).contextPath("/api")
                        .with(user(principal(Set.of("approval:view"))))
                        .contentType("application/json")
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_with_approve_perm_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvalService.approve(any(), any(), any(), eq(id), any())).thenReturn(
                ApprovalInstanceResponse.builder().id(id).status(InstanceStatus.APPROVED).build());

        mockMvc.perform(post("/api/v1/approvals/instances/{id}/approve", id).contextPath("/api")
                        .with(user(principal(Set.of("approval:approve"))))
                        .contentType("application/json")
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isOk());

        verify(approvalService).approve(any(), any(), any(), eq(id), any());
    }

    @Test
    void reject_without_reject_perm_forbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/approvals/instances/{id}/reject", id).contextPath("/api")
                        .with(user(principal(Set.of("approval:view"))))
                        .contentType("application/json")
                        .content("{\"comment\":\"no\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_with_reject_perm_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvalService.reject(any(), any(), any(), eq(id), any())).thenReturn(
                ApprovalInstanceResponse.builder().id(id).status(InstanceStatus.REJECTED).build());

        mockMvc.perform(post("/api/v1/approvals/instances/{id}/reject", id).contextPath("/api")
                        .with(user(principal(Set.of("approval:reject"))))
                        .contentType("application/json")
                        .content("{\"comment\":\"不符合规范\"}"))
                .andExpect(status().isOk());

        verify(approvalService).reject(any(), any(), any(), eq(id), any());
    }
}
