package com.itam.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 解析 Authorization Bearer 头，重建 JwtUserPrincipal 并写入 SecurityContext。
 * 无 token / 解析失败 / access 已被登出拉黑 → 不写入认证，交由 EntryPoint 返回 401。
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;

    public JwtFilter(JwtUtil jwtUtil, RefreshTokenStore refreshTokenStore) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Claims claims = jwtUtil.parse(token);
                UUID jti = jwtUtil.getJti(claims);
                if (refreshTokenStore.isBlacklisted(jti)) {
                    log.debug("Access token blacklisted: {}", jti);
                } else if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    JwtUserPrincipal principal = toPrincipal(claims, jti);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ex) {
                log.debug("JWT parse failed: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private JwtUserPrincipal toPrincipal(Claims claims, UUID jti) {
        UUID userId = jwtUtil.getSubject(claims);
        UserType userType = jwtUtil.getUserType(claims);
        UUID tenantId = jwtUtil.getTenantId(claims);
        String perms = claims.get("perms", String.class);
        Set<String> permissions = new HashSet<>();
        if (perms != null && !perms.isBlank()) {
            for (String p : perms.split(",")) {
                if (!p.isBlank()) permissions.add(p.trim());
            }
        }
        Set<String> roles = jwtUtil.getRoles(claims);
        boolean mustChange = Boolean.TRUE.equals(claims.get("mcp", Boolean.class));
        return new JwtUserPrincipal(userId,
                claims.get("uname", String.class),
                claims.get("dname", String.class),
                userType, tenantId, jti, roles, permissions, mustChange);
    }
}
