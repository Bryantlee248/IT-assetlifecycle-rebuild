package com.itam.notification.controller;

import com.itam.notification.application.NotificationService;
import com.itam.notification.dto.NotificationResponse;
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
 * NotificationController 接口层测试（@WebMvcTest）：聚焦权限码门禁。
 * 权限码：notification:view（列表/未读数）、notification:read（标记已读）。
 * 鉴权门禁模式完整复制 LifecycleControllerTest / ApprovalControllerTest。
 */
@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class NotificationControllerTest {

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
    private NotificationService notificationService;

    private final UUID tenantId = UUID.randomUUID();

    private JwtUserPrincipal principal(Set<String> perms) {
        return new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantId, UUID.randomUUID(),
                Set.of("asset_admin"), perms, false);
    }

    @Test
    void unauthenticated_list_requiresAuth() throws Exception {
        doAnswer(inv -> {
            HttpServletResponse response = inv.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(entryPoint).commence(any(), any(), any());

        mockMvc.perform(get("/api/v1/notifications").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void without_notification_view_list_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").contextPath("/api")
                        .with(user(principal(Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void with_notification_view_list_ok() throws Exception {
        when(notificationService.list(any(), any(), any())).thenReturn(
                List.of(NotificationResponse.builder().id(UUID.randomUUID()).title("n1").build()));

        mockMvc.perform(get("/api/v1/notifications").contextPath("/api")
                        .with(user(principal(Set.of("notification:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void with_notification_view_unreadCount_ok() throws Exception {
        when(notificationService.unreadCount(any(), any())).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").contextPath("/api")
                        .with(user(principal(Set.of("notification:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void markRead_without_notification_read_forbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).contextPath("/api")
                        .with(user(principal(Set.of("notification:view")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void markRead_with_notification_read_ok() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).contextPath("/api")
                        .with(user(principal(Set.of("notification:read")))))
                .andExpect(status().isOk());

        verify(notificationService).markRead(any(), any(), eq(id));
    }

    @Test
    void markAllRead_with_notification_read_ok() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all").contextPath("/api")
                        .with(user(principal(Set.of("notification:read")))))
                .andExpect(status().isOk());

        verify(notificationService).markAllRead(any(), any());
    }
}
