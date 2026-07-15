package com.itam.lifecycle.controller;

import com.itam.lifecycle.application.LifecycleAppService;
import com.itam.lifecycle.dto.ExecuteActionRequest;
import com.itam.lifecycle.dto.LifecycleActionResult;
import com.itam.lifecycle.dto.LifecycleActionResponse;
import com.itam.lifecycle.dto.LifecycleEventResponse;
import com.itam.lifecycle.dto.LifecycleStatusResponse;
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
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
 * LifecycleController 接口层测试（@WebMvcTest）：聚焦鉴权门禁与基本路由。
 * 鉴权门禁模式完整复制 AssetControllerTest。
 */
@WebMvcTest(controllers = LifecycleController.class)
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class LifecycleControllerTest {

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
    private LifecycleAppService lifecycleAppService;

    private final UUID tenantId = UUID.randomUUID();

    private JwtUserPrincipal principal(Set<String> perms) {
        return new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantId, UUID.randomUUID(),
                Set.of("asset_admin"), perms, false);
    }

    @Test
    void path_variables_declare_names_for_compilation_without_parameter_metadata() {
        for (Method method : LifecycleController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                if (pathVariable != null) {
                    assertThat(pathVariable.value()).isNotBlank();
                }
            }
        }
    }

    @Test
    void unauthenticated_get_requiresAuth() throws Exception {
        // 模拟真实 RestAuthenticationEntryPoint：未认证返回 401。
        doAnswer(inv -> {
            HttpServletResponse response = inv.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(entryPoint).commence(any(), any(), any());

        mockMvc.perform(
                        get("/api/v1/assets/{id}/lifecycle", UUID.randomUUID()).contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_without_lifecycle_view_get_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/assets/{id}/lifecycle", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("asset:view")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_with_view_without_transition_post_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/assets/{id}/lifecycle/actions/deploy", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("lifecycle:view"))))
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"formData\":{},\"attachmentIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_with_view_get_status_ok() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(lifecycleAppService.getStatus(any(), eq(assetId))).thenReturn(
                LifecycleStatusResponse.builder().assetId(assetId).currentState("planned").build());
        mockMvc.perform(get("/api/v1/assets/{id}/lifecycle", assetId).contextPath("/api")
                        .with(user(principal(Set.of("lifecycle:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticated_with_view_get_events_ok() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(lifecycleAppService.getEvents(any(), eq(assetId))).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/assets/{id}/lifecycle/events", assetId).contextPath("/api")
                        .with(user(principal(Set.of("lifecycle:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticated_with_view_get_actions_ok() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(lifecycleAppService.getActions(any(), eq(assetId))).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/assets/{id}/lifecycle/actions", assetId).contextPath("/api")
                        .with(user(principal(Set.of("lifecycle:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticated_with_view_and_transition_post_action_ok() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(lifecycleAppService.executeAction(any(), any(), any(), any(), eq("deploy"), any(ExecuteActionRequest.class)))
                .thenReturn(new LifecycleActionResult("transitioned", "planned", "in_use", null, UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/assets/{id}/lifecycle/actions/deploy", assetId).contextPath("/api")
                        .with(user(principal(Set.of("lifecycle:view", "lifecycle:transition"))))
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"formData\":{},\"attachmentIds\":[]}"))
                .andExpect(status().isOk());

        // 证明 JSON body 已正确反序列化为 ExecuteActionRequest（非 400）
        verify(lifecycleAppService).executeAction(any(), any(), any(), any(), eq("deploy"), any(ExecuteActionRequest.class));
    }
}
