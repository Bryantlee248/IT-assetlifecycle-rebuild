package com.itam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * 从 SecurityContext 解析当前登录用户与租户上下文的静态工具。
 * 服务层/Repository 层据此强制注入 tenant_id，前端不可伪造。
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static Optional<JwtUserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static UUID getCurrentUserId() {
        return currentPrincipal().map(JwtUserPrincipal::getUserId).orElse(null);
    }

    public static UUID getCurrentTenantId() {
        return currentPrincipal().map(JwtUserPrincipal::getTenantId).orElse(null);
    }

    public static boolean isPlatform() {
        return currentPrincipal().map(p -> p.getUserType() == UserType.PLATFORM).orElse(false);
    }

    public static boolean isTenant() {
        return currentPrincipal().map(p -> p.getUserType() == UserType.TENANT).orElse(false);
    }
}
