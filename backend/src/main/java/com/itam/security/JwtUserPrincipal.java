package com.itam.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 解析后注入 SecurityContext 的 principal。
 * 权限码直接映射为 Spring Security 的 authority，供 @PreAuthorize("hasAuthority('xxx')") 使用。
 *
 * MVP-1 增强：新增 roles（角色码集合），供字段权限默认引擎按角色解析可见/可编辑/脱敏。
 * 旧 8 参构造保留用于向后兼容（角色集合默认为空）。
 */
@Getter
public class JwtUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String username;
    private final String displayName;
    private final UserType userType;
    private final UUID tenantId; // 平台用户为 null
    private final UUID jti;       // 当前 access token 的 jti
    private final Set<String> roles; // 角色码，如 tenant_admin / asset_admin / auditor
    private final Set<String> permissions;
    private final boolean mustChangePassword;

    public JwtUserPrincipal(UUID userId, String username, String displayName,
                            UserType userType, UUID tenantId, UUID jti,
                            Set<String> roles, Set<String> permissions, boolean mustChangePassword) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.userType = userType;
        this.tenantId = tenantId;
        this.jti = jti;
        this.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles);
        this.permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
        this.mustChangePassword = mustChangePassword;
    }

    /** 向后兼容构造（MVP-0 测试沿用）：角色集合默认空。 */
    public JwtUserPrincipal(UUID userId, String username, String displayName,
                            UserType userType, UUID tenantId, UUID jti,
                            Set<String> permissions, boolean mustChangePassword) {
        this(userId, username, displayName, userType, tenantId, jti,
                Set.of(), permissions, mustChangePassword);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
