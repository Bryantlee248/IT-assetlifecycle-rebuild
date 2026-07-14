package com.itam.asset.controller;

import com.itam.asset.application.AssetAppService;
import com.itam.asset.application.AssetRelationAppService;
import com.itam.security.JwtFilter;
import com.itam.security.JwtUserPrincipal;
import com.itam.security.JwtUtil;
import com.itam.security.MustChangePasswordFilter;
import com.itam.security.RefreshTokenStore;
import com.itam.security.RestAccessDeniedHandler;
import com.itam.security.RestAuthenticationEntryPoint;
import com.itam.security.SecurityConfig;
import com.itam.security.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssetController 接口层测试（@WebMvcTest）：聚焦鉴权门禁与基本路由。
 */
@WebMvcTest(controllers = AssetController.class)
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class AssetControllerTest {

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
    private AssetAppService assetAppService;
    @MockBean
    private AssetRelationAppService assetRelationAppService;

    private final UUID tenantId = UUID.randomUUID();

    private JwtUserPrincipal principal(Set<String> perms) {
        return new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantId, UUID.randomUUID(),
                Set.of("asset_admin"), perms, false);
    }

    @Test
    void list_requires_asset_view() throws Exception {
        when(assetAppService.list(any(), any(), any(), any())).thenReturn(null);
        mockMvc.perform(get("/api/v1/assets").contextPath("/api")
                        .with(user(principal(Set.of("asset:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void list_forbidden_without_asset_view() throws Exception {
        mockMvc.perform(get("/api/v1/assets").contextPath("/api")
                        .with(user(principal(Set.of("metadata:manage")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_requires_asset_create() throws Exception {
        when(assetAppService.create(any(), any(), any(), any())).thenReturn(null);
        mockMvc.perform(post("/api/v1/assets").contextPath("/api")
                        .with(user(principal(Set.of("asset:create"))))
                        .contentType("application/json")
                        .content("{\"assetTypeId\":\"" + UUID.randomUUID() + "\",\"assetName\":\"x\",\"assetNo\":\"A1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_requires_asset_update() throws Exception {
        when(assetAppService.update(any(), any(), any(), any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/assets/{id}", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("asset:update"))))
                        .contentType("application/json")
                        .content("{\"assetName\":\"y\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_requires_asset_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/assets/{id}", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("asset:delete")))))
                .andExpect(status().isOk());
    }
}
