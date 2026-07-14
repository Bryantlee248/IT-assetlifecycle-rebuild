package com.itam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itam.common.result.ApiResponse;
import com.itam.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MustChangePasswordFilter 纯单元测试（无 Spring 上下文，不依赖 PG/Redis）。
 * 通过手动构造 JwtUserPrincipal 并写入 SecurityContextHolder 模拟已认证请求。
 */
class MustChangePasswordFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MustChangePasswordFilter filter = new MustChangePasswordFilter(objectMapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtUserPrincipal principal(boolean mustChangePassword, UserType userType) {
        return new JwtUserPrincipal(UUID.randomUUID(), "user", "User", userType, UUID.randomUUID(),
                UUID.randomUUID(), Set.of("user:list"), mustChangePassword);
    }

    private void authenticate(JwtUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, Collections.singletonList(new SimpleGrantedAuthority("user:list"))));
    }

    private MockHttpServletRequest request(String method, String servletPath) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setServletPath(servletPath);
        req.setRequestURI(servletPath);
        return req;
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    @Test
    void mcp_true_change_password_is_allowed() throws Exception {
        authenticate(principal(true, UserType.TENANT));
        MockHttpServletRequest req = request("POST", "/v1/auth/change-password");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, response(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void mcp_true_logout_is_allowed() throws Exception {
        authenticate(principal(true, UserType.TENANT));
        MockHttpServletRequest req = request("POST", "/v1/auth/logout");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, response(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void mcp_true_me_is_allowed() throws Exception {
        authenticate(principal(true, UserType.TENANT));
        MockHttpServletRequest req = request("GET", "/v1/auth/me");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, response(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void mcp_true_other_endpoint_is_blocked_with_403() throws Exception {
        authenticate(principal(true, UserType.TENANT));
        MockHttpServletRequest req = request("GET", "/v1/tenant/users");
        MockHttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        ApiResponse<?> body = objectMapper.readValue(res.getContentAsString(), ApiResponse.class);
        assertThat(body.getCode()).isEqualTo(ResultCode.MUST_CHANGE_PASSWORD.getCode());
        assertThat(body.getMessage()).contains("请先修改初始密码");
    }

    @Test
    void mcp_true_menu_endpoint_is_blocked_with_403() throws Exception {
        authenticate(principal(true, UserType.TENANT));
        MockHttpServletRequest req = request("GET", "/v1/auth/menu");
        MockHttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        ApiResponse<?> body = objectMapper.readValue(res.getContentAsString(), ApiResponse.class);
        assertThat(body.getCode()).isEqualTo(ResultCode.MUST_CHANGE_PASSWORD.getCode());
        assertThat(body.getMessage()).contains("请先修改初始密码");
    }

    @Test
    void mcp_false_any_endpoint_is_allowed() throws Exception {
        authenticate(principal(false, UserType.TENANT));
        MockHttpServletRequest req = request("GET", "/v1/tenant/users");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, response(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void anonymous_request_passes_through() throws Exception {
        // 无认证信息
        MockHttpServletRequest req = request("GET", "/v1/tenant/users");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, response(), chain);
        verify(chain).doFilter(any(), any());
    }
}
