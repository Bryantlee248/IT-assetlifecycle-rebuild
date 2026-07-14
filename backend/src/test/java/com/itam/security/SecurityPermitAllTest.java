package com.itam.security;

import com.itam.auth.AuthController;
import com.itam.auth.AuthService;
import com.itam.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校验 SecurityConfig 中 permitAll 的匿名端点（已去掉 /api 前缀）：
 * 无 token 访问登录/刷新/健康检查 不应返回 401。
 * 仅加载 Web 层并 Mock 掉下游依赖，不连接 PG/Redis。
 */
@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({SecurityConfig.class, JwtFilter.class, MustChangePasswordFilter.class})
class SecurityPermitAllTest {

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
    private AuthService authService;
    @MockBean
    private DataSource dataSource;
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void login_endpoint_is_permit_all() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contextPath("/api")
                        .contentType("application/json").content("{}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void refresh_endpoint_is_permit_all() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").contextPath("/api")
                        .contentType("application/json").content("{}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void health_endpoint_is_permit_all() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health").contextPath("/api"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void health_endpoint_returns_service_unavailable_when_dependencies_are_down() throws Exception {
        mockMvc.perform(get("/api/v1/health").contextPath("/api"))
                .andExpect(status().isServiceUnavailable());
    }
}
