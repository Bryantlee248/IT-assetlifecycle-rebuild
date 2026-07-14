package com.itam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 工具：签发 Access/Refresh Token、解析 Claims。
 * Secret 来自配置；Access 短时效(15min)，Refresh 长时效(7d) 且携带唯一 jti 用于轮换/吊销。
 * jti 由调用方生成并传入，便于在 Redis 中建立 access<->refresh 关联以实现轮换与登出失效。
 *
 * MVP-1 增强：Access Token 额外携带 roles 声明（逗号分隔角色码），供字段权限引擎使用。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTtlMillis;
    private final long refreshTtlMillis;

    public JwtUtil(@Value("${itam.jwt.secret}") String secret,
                   @Value("${itam.jwt.access-token-ttl-minutes:15}") long accessTtlMinutes,
                   @Value("${itam.jwt.refresh-token-ttl-days:7}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMinutes * 60_000L;
        this.refreshTtlMillis = refreshTtlDays * 24 * 60 * 60 * 1000L;
    }

    public String generateAccessToken(UUID accessJti, UUID userId, String username, String displayName,
                                       UserType userType, UUID tenantId,
                                       Set<String> permissions, boolean mustChangePassword) {
        return generateAccessToken(accessJti, userId, username, displayName, userType, tenantId,
                Set.of(), permissions, mustChangePassword);
    }

    public String generateAccessToken(UUID accessJti, UUID userId, String username, String displayName,
                                       UserType userType, UUID tenantId,
                                       Set<String> roles, Set<String> permissions,
                                       boolean mustChangePassword) {
        Instant now = Instant.now();
        String rolesClaim = roles == null || roles.isEmpty() ? null : String.join(",", roles);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("uname", username)
                .claim("dname", displayName)
                .claim("typ", userType.name())
                .claim("ten", tenantId != null ? tenantId.toString() : null)
                .claim("roles", rolesClaim)
                .claim("perms", String.join(",", permissions))
                .claim("mcp", mustChangePassword)
                .claim("jti", accessJti.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTtlMillis)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID refreshJti, UUID userId, UserType userType, UUID tenantId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", userType.name())
                .claim("ten", tenantId != null ? tenantId.toString() : null)
                .claim("jti", refreshJti.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTtlMillis)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getJti(Claims claims) {
        return UUID.fromString(claims.get("jti", String.class));
    }

    public UUID getSubject(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserType getUserType(Claims claims) {
        return UserType.valueOf(claims.get("typ", String.class));
    }

    public UUID getTenantId(Claims claims) {
        String ten = claims.get("ten", String.class);
        return ten != null ? UUID.fromString(ten) : null;
    }

    public Set<String> getRoles(Claims claims) {
        String roles = claims.get("roles", String.class);
        Set<String> result = new java.util.LinkedHashSet<>();
        if (roles != null && !roles.isBlank()) {
            for (String r : roles.split(",")) {
                if (!r.isBlank()) result.add(r.trim());
            }
        }
        return result;
    }
}
