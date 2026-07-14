package com.itam.auth;

import com.itam.audit.AuditLogService;
import com.itam.auth.dto.*;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.platform.PlatformUser;
import com.itam.platform.PlatformUserRepository;
import com.itam.platform.Tenant;
import com.itam.platform.TenantRepository;
import com.itam.security.JwtUtil;
import com.itam.security.RefreshTokenStore;
import com.itam.security.TenantContext;
import com.itam.security.UserType;
import com.itam.tenantadmin.Permission;
import com.itam.tenantadmin.PermissionRepository;
import com.itam.tenantadmin.Role;
import com.itam.tenantadmin.RolePermission;
import com.itam.tenantadmin.RolePermissionRepository;
import com.itam.tenantadmin.RoleRepository;
import com.itam.tenantadmin.TenantUser;
import com.itam.tenantadmin.TenantUserRepository;
import lombok.Getter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证核心：登录/刷新(轮换)/登出/改密/当前用户/菜单/权限/可访问租户/租户切换。
 * 平台管理员无租户上下文、拥有全部权限；租户用户按所在租户角色聚合并强制 tenant_id 隔离。
 */
@Service
public class AuthService {

    private final PlatformUserRepository platformUserRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AuthService(PlatformUserRepository platformUserRepository,
                       TenantUserRepository tenantUserRepository,
                       TenantRepository tenantRepository,
                       RoleRepository roleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       PermissionRepository permissionRepository,
                       JwtUtil jwtUtil,
                       RefreshTokenStore refreshTokenStore,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.platformUserRepository = platformUserRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.jwtUtil = jwtUtil;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public LoginResponse login(LoginRequest req) {
        PlatformUser user = platformUserRepository.findByUsername(req.username()).orElse(null);
        if (user == null) {
            auditLogService.logAnon("LOGIN_FAIL", "AUTH", req.username(), null, Map.of("reason", "no such user"));
            throw new BusinessException(ResultCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            auditLogService.logAnon("LOGIN_FAIL", "AUTH", req.username(), user.getId(), Map.of("reason", "bad password"));
            throw new BusinessException(ResultCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "用户已停用");
        }

        UserType userType;
        UUID tenantId;
        Set<String> permissions;
        Set<String> roles = Set.of();
        if (user.isPlatformAdmin()) {
            userType = UserType.PLATFORM;
            tenantId = null;
            permissions = allPermissionCodes();
        } else {
            List<TenantUser> tus = tenantUserRepository.findByPlatformUserId(user.getId());
            if (tus.isEmpty()) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "用户未关联任何租户");
            }
            TenantUser def = tus.stream()
                    .filter(t -> "ACTIVE".equals(t.getStatus()))
                    .findFirst().orElse(tus.get(0));
            ResolvedContext rc = resolveTenant(user, def.getTenantId());
            userType = rc.userType();
            tenantId = rc.tenantId();
            permissions = rc.permissions();
            roles = rc.roles();
        }

        TokenResponse tokens = issueTokens(user, tenantId, permissions, roles, userType);
        auditLogService.log("LOGIN_SUCCESS", "AUTH", user.getId().toString(), Map.of("userType", userType.name()));
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), userType.name(),
                user.isMustChangePassword(), tenantId, user.getUsername(), user.getDisplayName());
    }

    public TokenResponse refresh(RefreshRequest req) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.parse(req.refreshToken());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REFRESH_INVALID);
        }
        UUID refreshJti = jwtUtil.getJti(claims);
        if (!refreshTokenStore.validate(refreshJti)) {
            throw new BusinessException(ResultCode.REFRESH_INVALID);
        }
        refreshTokenStore.remove(refreshJti); // 轮换：旧 refresh 立即失效

        UUID userId = jwtUtil.getSubject(claims);
        UserType userType = jwtUtil.getUserType(claims);
        UUID tenantId = jwtUtil.getTenantId(claims);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.REFRESH_INVALID));
        ResolvedContext rc = user.isPlatformAdmin()
                ? new ResolvedContext(UserType.PLATFORM, tenantId, allPermissionCodes(), Set.of())
                : resolveTenant(user, tenantId);
        return issueTokens(user, tenantId, rc.permissions(), rc.roles(), userType);
    }

    public void logout(com.itam.security.JwtUserPrincipal principal) {
        refreshTokenStore.removeRefreshByAccess(principal.getJti()); // 吊销当前 refresh
        refreshTokenStore.blacklist(principal.getJti());             // access 立即失效
        auditLogService.log("LOGOUT", "AUTH", principal.getUserId().toString(), null);
    }

    public void changePassword(com.itam.security.JwtUserPrincipal principal, ChangePasswordRequest req) {
        PlatformUser user = platformUserRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        platformUserRepository.save(user);
        refreshTokenStore.removeAllForUser(user.getId()); // 改密后所有会话失效
        auditLogService.log("CHANGE_PASSWORD", "PROFILE", user.getId().toString(), null);
    }

    public UserInfoResponse me(com.itam.security.JwtUserPrincipal principal) {
        PlatformUser user = platformUserRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getPhone(), principal.getUserType().name(),
                principal.getTenantId(), user.isMustChangePassword(),
                new ArrayList<>(principal.getPermissions()));
    }

    public List<String> permissions(com.itam.security.JwtUserPrincipal principal) {
        return new ArrayList<>(principal.getPermissions());
    }

    public List<TenantBrief> accessibleTenants(com.itam.security.JwtUserPrincipal principal) {
        if (principal.getUserType() == UserType.PLATFORM) {
            return List.of(); // 平台管理员无租户切换上下文
        }
        List<TenantBrief> result = new ArrayList<>();
        for (TenantUser tu : tenantUserRepository.findByPlatformUserId(principal.getUserId())) {
            if (!"ACTIVE".equals(tu.getStatus())) continue;
            tenantRepository.findById(tu.getTenantId()).ifPresent(t -> {
                if ("ACTIVE".equals(t.getStatus())) {
                    result.add(new TenantBrief(t.getId(), t.getName(), t.getCode(), t.getStatus()));
                }
            });
        }
        return result;
    }

    public TokenResponse switchTenant(com.itam.security.JwtUserPrincipal principal, UUID tenantId) {
        if (principal.getUserType() == UserType.PLATFORM) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "平台用户无租户上下文");
        }
        PlatformUser user = platformUserRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        ResolvedContext rc = resolveTenant(user, tenantId);
        refreshTokenStore.removeRefreshByAccess(principal.getJti()); // 轮换旧 refresh
        return issueTokens(user, rc.tenantId(), rc.permissions(), rc.roles(), UserType.TENANT);
    }

    public List<MenuNode> menu(com.itam.security.JwtUserPrincipal principal) {
        Set<String> perms = principal.getPermissions();
        List<MenuNode> nodes = new ArrayList<>();
        if (principal.getUserType() == UserType.PLATFORM && perms.contains("tenant:list")) {
            nodes.add(new MenuNode("tenant-mgmt", "租户管理", "/platform/tenants", "Office", List.of()));
        }
        if (perms.contains("org:list")) {
            nodes.add(new MenuNode("org", "组织管理", "/tenant/organizations", "Share", List.of()));
        }
        if (perms.contains("user:list")) {
            nodes.add(new MenuNode("user", "用户管理", "/tenant/users", "User", List.of()));
        }
        if (perms.contains("role:list")) {
            nodes.add(new MenuNode("role", "角色权限", "/tenant/roles", "Lock", List.of()));
        }
        // MVP-1：元数据配置分组（门禁 metadata:manage）
        if (perms.contains("metadata:manage")) {
            List<MenuNode> metaChildren = List.of(
                    new MenuNode("meta-types", "资产类型", "/metadata/asset-types", "Files", List.of()),
                    new MenuNode("meta-fields", "字段定义", "/metadata/asset-types?tab=fields", "Tickets", List.of()),
                    new MenuNode("meta-form", "表单配置", "/metadata/asset-types?tab=form", "Document", List.of()),
                    new MenuNode("meta-list", "列表配置", "/metadata/asset-types?tab=list", "List", List.of()),
                    new MenuNode("meta-search", "查询配置", "/metadata/asset-types?tab=search", "Search", List.of())
            );
            nodes.add(new MenuNode("meta-group", "元数据配置", "", "Setting", metaChildren));
        }
        // MVP-1：资产管理分组（门禁 asset:view）
        if (perms.contains("asset:view")) {
            List<MenuNode> assetChildren = List.of(
                    new MenuNode("asset-list", "资产列表", "/assets", "Box", List.of())
            );
            nodes.add(new MenuNode("asset-group", "资产管理", "", "Goods", assetChildren));
        }
        nodes.add(new MenuNode("profile", "个人中心", "/profile", "Setting", List.of()));
        return nodes;
    }

    // ===== 内部工具 =====

    private TokenResponse issueTokens(PlatformUser user, UUID tenantId, Set<String> permissions,
                                      Set<String> roles, UserType userType) {
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        String access = jwtUtil.generateAccessToken(accessJti, user.getId(), user.getUsername(),
                user.getDisplayName(), userType, tenantId, roles, permissions, user.isMustChangePassword());
        String refresh = jwtUtil.generateRefreshToken(refreshJti, user.getId(), userType, tenantId);
        refreshTokenStore.store(refreshJti, user.getId(), userType, tenantId);
        refreshTokenStore.linkAccess(accessJti, refreshJti);
        return new TokenResponse(access, refresh);
    }

    private ResolvedContext resolveTenant(PlatformUser user, UUID tenantId) {
        TenantUser tu = tenantUserRepository.findByTenantIdAndPlatformUserId(tenantId, user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.NO_PERMISSION, "无该租户访问权限"));
        if (!"ACTIVE".equals(tu.getStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "租户用户已停用");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租户不存在"));
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "租户已停用");
        }
        Set<String> perms = new HashSet<>();
        Set<String> roles = new HashSet<>();
        if (tu.getRoleId() != null) {
            for (RolePermission rp : rolePermissionRepository.findByTenantIdAndRoleId(tenantId, tu.getRoleId())) {
                perms.add(rp.getPermissionCode());
            }
            roleRepository.findById(tu.getRoleId()).ifPresent(r -> roles.add(r.getCode()));
        }
        return new ResolvedContext(UserType.TENANT, tenantId, perms, roles);
    }

    private Set<String> allPermissionCodes() {
        return permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    private record ResolvedContext(UserType userType, UUID tenantId, Set<String> permissions, Set<String> roles) {
    }
}
