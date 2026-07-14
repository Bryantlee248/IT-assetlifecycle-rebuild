package com.itam.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 单元测试（无需 Spring 上下文）。
 * 校验 Access/Refresh Token 的生成与解析往返、claims 字段（sub/typ/ten/jti/perms/mcp）正确。
 */
class JwtUtilTest {

    // 密钥长度需 >= 32 字节以满足 HS256 最低要求
    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-which-is-long-enough-32bytes-abcdefghijk", 15, 7);

    @Test
    void access_token_round_trip() {
        UUID jti = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> perms = Set.of("org:list", "user:list");

        String token = jwtUtil.generateAccessToken(jti, userId, "alice", "Alice",
                UserType.TENANT, tenantId, perms, true);

        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.getJti(claims)).isEqualTo(jti);
        assertThat(jwtUtil.getSubject(claims)).isEqualTo(userId);
        assertThat(jwtUtil.getUserType(claims)).isEqualTo(UserType.TENANT);
        assertThat(jwtUtil.getTenantId(claims)).isEqualTo(tenantId);
    }

    @Test
    void platform_user_has_null_tenant() {
        UUID jti = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateAccessToken(jti, userId, "admin", "Admin",
                UserType.PLATFORM, null, Set.of("tenant:list"), false);

        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.getUserType(claims)).isEqualTo(UserType.PLATFORM);
        assertThat(jwtUtil.getTenantId(claims)).isNull();
    }

    @Test
    void refresh_token_carries_subject_and_type() {
        UUID jti = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String refresh = jwtUtil.generateRefreshToken(jti, userId, UserType.TENANT, tenantId);
        Claims claims = jwtUtil.parse(refresh);

        assertThat(jwtUtil.getJti(claims)).isEqualTo(jti);
        assertThat(jwtUtil.getSubject(claims)).isEqualTo(userId);
        assertThat(jwtUtil.getUserType(claims)).isEqualTo(UserType.TENANT);
        assertThat(jwtUtil.getTenantId(claims)).isEqualTo(tenantId);
    }
}
