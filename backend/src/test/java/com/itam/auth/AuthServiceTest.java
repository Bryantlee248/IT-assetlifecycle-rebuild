package com.itam.auth;

import com.itam.audit.AuditLogService;
import com.itam.auth.dto.ChangePasswordRequest;
import com.itam.auth.dto.LoginRequest;
import com.itam.auth.dto.LoginResponse;
import com.itam.auth.dto.RefreshRequest;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.platform.PlatformUser;
import com.itam.platform.PlatformUserRepository;
import com.itam.platform.Tenant;
import com.itam.platform.TenantRepository;
import com.itam.security.JwtUtil;
import com.itam.security.JwtUserPrincipal;
import com.itam.security.RefreshTokenStore;
import com.itam.security.UserType;
import com.itam.tenantadmin.Permission;
import com.itam.tenantadmin.PermissionRepository;
import com.itam.tenantadmin.RolePermission;
import com.itam.tenantadmin.RolePermissionRepository;
import com.itam.tenantadmin.RoleRepository;
import com.itam.tenantadmin.TenantUser;
import com.itam.tenantadmin.TenantUserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试（Mockito，无 Spring 上下文，无需 DB/Redis）。
 * 覆盖登录(平台/租户)、刷新轮换、登出、改密、可访问租户、租户切换、菜单等核心逻辑与隔离约束。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock PlatformUserRepository platformUserRepository;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RoleRepository roleRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock JwtUtil jwtUtil;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditLogService auditLogService;

    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final UUID accessJti = UUID.randomUUID();
    private final UUID refreshJti = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authService = new AuthService(platformUserRepository, tenantUserRepository,
                tenantRepository, roleRepository, rolePermissionRepository, permissionRepository,
                jwtUtil, refreshTokenStore, passwordEncoder, auditLogService);
    }

    // ---------- 登录：平台管理员 ----------

    @Test
    void login_platform_admin_success() {
        PlatformUser admin = platformAdmin();
        when(platformUserRepository.findByUsername("platform_admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Platform@123", admin.getPasswordHash())).thenReturn(true);
        when(permissionRepository.findAll()).thenReturn(allPermissions());
        when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh-token");

        LoginResponse r = authService.login(new LoginRequest("platform_admin", "Platform@123"));

        assertThat(r.accessToken()).isEqualTo("access-token");
        assertThat(r.refreshToken()).isEqualTo("refresh-token");
        assertThat(r.userType()).isEqualTo("PLATFORM");
        assertThat(r.mustChangePassword()).isFalse();
        assertThat(r.tenantId()).isNull();
        // refresh jti 由内部随机生成，仅校验调用参数(userId/type/tenant)正确
        verify(refreshTokenStore).store(any(UUID.class), eq(userId), eq(UserType.PLATFORM), eq(null));
        verify(auditLogService).log(eq("LOGIN_SUCCESS"), eq("AUTH"), anyString(), any());
    }

    @Test
    void login_user_not_found() {
        when(platformUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.UNAUTHENTICATED);
        verify(auditLogService).logAnon(eq("LOGIN_FAIL"), eq("AUTH"), anyString(), any(), any());
    }

    @Test
    void login_bad_password_audited() {
        PlatformUser user = platformAdmin();
        when(platformUserRepository.findByUsername("platform_admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new LoginRequest("platform_admin", "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.UNAUTHENTICATED);
        verify(auditLogService).logAnon(eq("LOGIN_FAIL"), eq("AUTH"), anyString(), any(), any());
    }

    @Test
    void login_disabled_user_forbidden() {
        PlatformUser user = platformAdmin();
        user.setStatus("DISABLED");
        when(platformUserRepository.findByUsername("platform_admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Platform@123", user.getPasswordHash())).thenReturn(true);
        assertThatThrownBy(() -> authService.login(new LoginRequest("platform_admin", "Platform@123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.NO_PERMISSION);
    }

    // ---------- 登录：租户用户 ----------

    @Test
    void login_tenant_user_success_with_tenant_context() {
        PlatformUser user = tenantUser();
        TenantUser tu = new TenantUser();
        tu.setTenantId(tenantId);
        tu.setStatus("ACTIVE");
        tu.setRoleId(roleId);

        when(platformUserRepository.findByUsername("tenant_admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Tenant@123", user.getPasswordHash())).thenReturn(true);
        when(tenantUserRepository.findByPlatformUserId(userId)).thenReturn(List.of(tu));
        when(tenantUserRepository.findByTenantIdAndPlatformUserId(tenantId, userId)).thenReturn(Optional.of(tu));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant()));
        when(rolePermissionRepository.findByTenantIdAndRoleId(tenantId, roleId))
                .thenReturn(List.of(rp("org:list"), rp("user:list")));
        when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh-token");

        LoginResponse r = authService.login(new LoginRequest("tenant_admin", "Tenant@123"));
        assertThat(r.userType()).isEqualTo("TENANT");
        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.mustChangePassword()).isTrue(); // 种子 mustChangePassword=true
        verify(refreshTokenStore).store(any(UUID.class), eq(userId), eq(UserType.TENANT), eq(tenantId));
    }

    @Test
    void login_tenant_user_without_tenant_link_forbidden() {
        PlatformUser user = tenantUser();
        when(platformUserRepository.findByUsername("tenant_admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Tenant@123", user.getPasswordHash())).thenReturn(true);
        when(tenantUserRepository.findByPlatformUserId(userId)).thenReturn(List.of());
        assertThatThrownBy(() -> authService.login(new LoginRequest("tenant_admin", "Tenant@123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.NO_PERMISSION);
    }

    // ---------- 刷新（轮换） ----------

    @Test
    void refresh_rotates_old_token() {
        Claims claims = claims();
        when(jwtUtil.parse("refresh-token")).thenReturn(claims);
        when(jwtUtil.getJti(claims)).thenReturn(refreshJti);
        when(refreshTokenStore.validate(refreshJti)).thenReturn(true);
        when(jwtUtil.getSubject(claims)).thenReturn(userId);
        when(jwtUtil.getUserType(claims)).thenReturn(UserType.TENANT);
        when(jwtUtil.getTenantId(claims)).thenReturn(tenantId);
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(tenantUser()));
        when(tenantUserRepository.findByTenantIdAndPlatformUserId(tenantId, userId))
                .thenReturn(Optional.of(activeTenantUser()));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant()));
        when(rolePermissionRepository.findByTenantIdAndRoleId(tenantId, roleId))
                .thenReturn(List.of(rp("org:list")));
        when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(any(), any(), any(), any())).thenReturn("new-refresh");

        RefreshRequest req = new RefreshRequest("refresh-token");
        var r = authService.refresh(req);

        assertThat(r.accessToken()).isEqualTo("new-access");
        assertThat(r.refreshToken()).isEqualTo("new-refresh");
        // 旧 refresh 必须被删除以实现"用过的 refresh 不可再用"
        verify(refreshTokenStore).remove(refreshJti);
    }

    @Test
    void refresh_invalid_when_store_missing() {
        Claims claims = claims();
        when(jwtUtil.parse("refresh-token")).thenReturn(claims);
        when(jwtUtil.getJti(claims)).thenReturn(refreshJti);
        when(refreshTokenStore.validate(refreshJti)).thenReturn(false);
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.REFRESH_INVALID);
    }

    // ---------- 登出 ----------

    @Test
    void logout_revokes_refresh_and_blacklists_access() {
        JwtUserPrincipal p = principal(UserType.TENANT, tenantId, Set.of("org:list"));
        authService.logout(p);
        verify(refreshTokenStore).removeRefreshByAccess(accessJti);
        verify(refreshTokenStore).blacklist(accessJti);
        verify(auditLogService).log(eq("LOGOUT"), eq("AUTH"), anyString(), any());
    }

    // ---------- 改密 ----------

    @Test
    void change_password_success_revocates_sessions() {
        PlatformUser user = platformAdmin();
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Platform@123", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");

        authService.changePassword(principal(UserType.PLATFORM, null, Set.of()),
                new ChangePasswordRequest("Platform@123", "NewPass@123"));

        ArgumentCaptor<PlatformUser> cap = ArgumentCaptor.forClass(PlatformUser.class);
        verify(platformUserRepository).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("new-hash");
        assertThat(cap.getValue().isMustChangePassword()).isFalse();
        verify(refreshTokenStore).removeAllForUser(userId);
    }

    @Test
    void change_password_wrong_old_fails() {
        PlatformUser user = platformAdmin();
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", user.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> authService.changePassword(principal(UserType.PLATFORM, null, Set.of()),
                new ChangePasswordRequest("bad", "NewPass@123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.PARAM_ERROR);
        verify(platformUserRepository, never()).save(any());
    }

    // ---------- 租户上下文隔离 ----------

    @Test
    void accessible_tenants_empty_for_platform() {
        var r = authService.accessibleTenants(principal(UserType.PLATFORM, null, Set.of()));
        assertThat(r).isEmpty();
    }

    @Test
    void switch_tenant_forbidden_for_platform() {
        assertThatThrownBy(() -> authService.switchTenant(
                principal(UserType.PLATFORM, null, Set.of()), tenantId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.NO_PERMISSION);
    }

    @Test
    void menu_for_platform_includes_tenant_mgmt() {
        var nodes = authService.menu(principal(UserType.PLATFORM, null, Set.of("tenant:list", "org:list")));
        assertThat(nodes).anyMatch(n -> "tenant-mgmt".equals(n.key()));
    }

    @Test
    void menu_for_tenant_excludes_tenant_mgmt() {
        var nodes = authService.menu(principal(UserType.TENANT, tenantId, Set.of("org:list")));
        assertThat(nodes).noneMatch(n -> "tenant-mgmt".equals(n.key()));
        assertThat(nodes).anyMatch(n -> "profile".equals(n.key()));
    }

    // ---------- 辅助构造 ----------

    private PlatformUser platformAdmin() {
        PlatformUser u = new PlatformUser();
        u.setId(userId);
        u.setUsername("platform_admin");
        u.setPasswordHash("hash");
        u.setDisplayName("平台管理员");
        u.setStatus("ACTIVE");
        u.setMustChangePassword(false);
        u.setPlatformAdmin(true);
        return u;
    }

    private PlatformUser tenantUser() {
        PlatformUser u = new PlatformUser();
        u.setId(userId);
        u.setUsername("tenant_admin");
        u.setPasswordHash("hash");
        u.setStatus("ACTIVE");
        u.setMustChangePassword(true);
        u.setPlatformAdmin(false);
        return u;
    }

    private Tenant activeTenant() {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("演示租户");
        t.setCode("demo");
        t.setStatus("ACTIVE");
        return t;
    }

    private TenantUser activeTenantUser() {
        TenantUser tu = new TenantUser();
        tu.setTenantId(tenantId);
        tu.setStatus("ACTIVE");
        tu.setRoleId(roleId);
        return tu;
    }

    private Claims claims() {
        return org.mockito.Mockito.mock(Claims.class);
    }

    private RolePermission rp(String code) {
        RolePermission rp = new RolePermission();
        rp.setPermissionCode(code);
        return rp;
    }

    private List<Permission> allPermissions() {
        return List.of(
                perm("tenant:list"), perm("tenant:create"), perm("org:list"), perm("user:list"), perm("role:list"));
    }

    private Permission perm(String code) {
        Permission p = new Permission();
        p.setCode(code);
        return p;
    }

    private JwtUserPrincipal principal(UserType type, UUID ten, Set<String> perms) {
        return new JwtUserPrincipal(userId, "user", "User", type, ten, accessJti, perms, false);
    }
}
