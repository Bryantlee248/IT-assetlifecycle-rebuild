package com.itam.security;

import com.itam.platform.TenantController;
import com.itam.platform.TenantService;
import com.itam.tenantadmin.OrganizationController;
import com.itam.tenantadmin.OrganizationService;
import com.itam.tenantadmin.RoleController;
import com.itam.tenantadmin.RolePermissionService;
import com.itam.tenantadmin.RoleService;
import com.itam.tenantadmin.UserController;
import com.itam.tenantadmin.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台域 / 租户域接口隔离校验：通过 JwtUserPrincipal 注入 SecurityContext 模拟两类用户。
 * 平台管理员访问租户端点 -> 403；租户管理员访问平台端点 -> 403；租户管理员访问本租户端点 -> 正常。
 * 仅加载 Web 层并 Mock 掉业务 service，不连接 PG/Redis。
 */
@WebMvcTest(controllers = {TenantController.class, UserController.class, RoleController.class, OrganizationController.class})
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class DomainIsolationTest {

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
    private TenantService tenantService;
    @MockBean
    private UserService userService;
    @MockBean
    private RoleService roleService;
    @MockBean
    private RolePermissionService rolePermissionService;
    @MockBean
    private OrganizationService organizationService;

    private JwtUserPrincipal platformAdmin() {
        return new JwtUserPrincipal(UUID.randomUUID(), "plat", "Plat", UserType.PLATFORM, null,
                UUID.randomUUID(), Set.of("tenant:list", "user:list"), false);
    }

    private JwtUserPrincipal tenantAdmin() {
        return new JwtUserPrincipal(UUID.randomUUID(), "ten", "Ten", UserType.TENANT, UUID.randomUUID(),
                UUID.randomUUID(), Set.of("user:list", "org:list"), false);
    }

    @Test
    void platform_admin_is_forbidden_from_tenant_endpoint() throws Exception {
        mockMvc.perform(get("/api/v1/tenant/users").contextPath("/api").with(user(platformAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenant_admin_is_forbidden_from_platform_endpoint() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants").contextPath("/api").with(user(tenantAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenant_admin_is_allowed_own_tenant_endpoint() throws Exception {
        mockMvc.perform(get("/api/v1/tenant/users").contextPath("/api").with(user(tenantAdmin())))
                .andExpect(status().isOk());
    }
}
