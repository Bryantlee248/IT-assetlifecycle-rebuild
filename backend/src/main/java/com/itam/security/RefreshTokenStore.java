package com.itam.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh Token 在 Redis 中的存储与轮换/吊销。
 * 键设计：
 *   itam:refresh:{refreshJti} -> "{userId}|{userType}|{tenantId}"  TTL=refreshTtl
 *   itam:access:{accessJti}   -> refreshJti                       TTL=accessTtl (用于登出/切换时反查并吊销 refresh)
 *   itam:blacklist:{accessJti}-> "1"                             TTL=accessTtl (登出后使 access 立即失效)
 */
@Service
public class RefreshTokenStore {

    private final StringRedisTemplate redis;
    private final long refreshTtlSeconds;
    private final long accessTtlSeconds;

    private static final String REFRESH_PREFIX = "itam:refresh:";
    private static final String ACCESS_PREFIX = "itam:access:";
    private static final String BLACKLIST_PREFIX = "itam:blacklist:";

    public RefreshTokenStore(StringRedisTemplate redis,
                             @Value("${itam.jwt.refresh-token-ttl-days:7}") long refreshTtlDays,
                             @Value("${itam.jwt.access-token-ttl-minutes:15}") long accessTtlMinutes) {
        this.redis = redis;
        this.refreshTtlSeconds = refreshTtlDays * 24L * 3600L;
        this.accessTtlSeconds = accessTtlMinutes * 60L;
    }

    public void store(UUID refreshJti, UUID userId, UserType userType, UUID tenantId) {
        String value = userId + "|" + userType.name() + "|" + (tenantId != null ? tenantId : "");
        redis.opsForValue().set(REFRESH_PREFIX + refreshJti, value, Duration.ofSeconds(refreshTtlSeconds));
    }

    public void linkAccess(UUID accessJti, UUID refreshJti) {
        redis.opsForValue().set(ACCESS_PREFIX + accessJti, refreshJti.toString(), Duration.ofSeconds(accessTtlSeconds));
    }

    public boolean validate(UUID refreshJti) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_PREFIX + refreshJti));
    }

    public void remove(UUID refreshJti) {
        redis.delete(REFRESH_PREFIX + refreshJti);
    }

    /** 登出/切换：通过 access jti 反查并吊销对应 refresh，并清除关联。 */
    public void removeRefreshByAccess(UUID accessJti) {
        String refreshJtiStr = redis.opsForValue().get(ACCESS_PREFIX + accessJti);
        if (refreshJtiStr != null) {
            redis.delete(REFRESH_PREFIX + UUID.fromString(refreshJtiStr));
            redis.delete(ACCESS_PREFIX + accessJti);
        }
    }

    /** 吊销某用户所有 refresh（改密/登出时调用）。 */
    public void removeAllForUser(UUID userId) {
        var keys = redis.keys(REFRESH_PREFIX + "*");
        if (keys == null) return;
        for (String k : keys) {
            String v = redis.opsForValue().get(k);
            if (v != null && v.startsWith(userId + "|")) {
                redis.delete(k);
            }
        }
    }

    public void blacklist(UUID accessJti) {
        redis.opsForValue().set(BLACKLIST_PREFIX + accessJti, "1", Duration.ofSeconds(accessTtlSeconds));
    }

    public boolean isBlacklisted(UUID accessJti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + accessJti));
    }
}
