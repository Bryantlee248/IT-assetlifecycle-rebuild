package com.itam.metadata.controller;

import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.application.AssetTypeAppService;
import com.itam.metadata.application.FieldAppService;
import com.itam.metadata.application.RuntimeMetadataService;
import com.itam.metadata.application.SchemaAppService;
import com.itam.metadata.repository.LocationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MetadataController 接口层测试（@WebMvcTest）：聚焦鉴权门禁与路由，
 * 业务 app service 全部 Mock，不连接数据库。
 */
@WebMvcTest(controllers = MetadataController.class)
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class MetadataControllerTest {

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
    private AssetTypeAppService assetTypeAppService;
    @MockBean
    private FieldAppService fieldAppService;
    @MockBean
    private SchemaAppService schemaAppService;
    @MockBean
    private RuntimeMetadataService runtimeMetadataService;
    @MockBean
    private LocationRepository locationRepository;

    private final UUID tenantId = UUID.randomUUID();

    private JwtUserPrincipal principal(Set<String> perms) {
        return new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantId, UUID.randomUUID(),
                Set.of("asset_admin"), perms, false);
    }

    @Test
    void asset_type_tree_requires_metadata_manage() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/asset-types/tree").contextPath("/api")
                        .with(user(principal(Set.of("metadata:manage")))))
                .andExpect(status().isOk());
    }

    @Test
    void asset_type_tree_forbidden_without_permission() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/asset-types/tree").contextPath("/api")
                        .with(user(principal(Set.of("asset:view")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void runtime_metadata_allows_asset_view() throws Exception {
        when(runtimeMetadataService.aggregate(any(), any(), any())).thenReturn(null);
        mockMvc.perform(get("/api/v1/metadata/runtime/asset-types/{id}", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("asset:view")))))
                .andExpect(status().isOk());
    }

    @Test
    void create_field_returns_409_on_code_conflict() throws Exception {
        when(fieldAppService.createField(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ResultCode.FIELD_CODE_CONFLICT));
        mockMvc.perform(post("/api/v1/metadata/asset-types/{id}/fields", UUID.randomUUID()).contextPath("/api")
                        .with(user(principal(Set.of("metadata:manage"))))
                        .contentType("application/json")
                        .content("{\"fieldCode\":\"cpu\",\"fieldName\":\"CPU\",\"fieldType\":\"integer\"}"))
                .andExpect(status().isConflict());
    }
}
