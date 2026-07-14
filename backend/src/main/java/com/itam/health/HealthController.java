package com.itam.health;

import com.itam.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：返回 PostgreSQL 与 Redis 状态。无需认证。
 */
@RestController
@RequestMapping("/v1/health")
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String pg = checkPostgres();
        String redis = checkRedis();
        Map<String, String> status = new LinkedHashMap<>();
        status.put("pg", pg);
        status.put("redis", redis);
        status.put("timestamp", java.time.Instant.now().toString());
        return ApiResponse.success(status);
    }

    private String checkPostgres() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            conn.ping();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
