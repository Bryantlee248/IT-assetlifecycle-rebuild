package com.itam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itam.common.result.ApiResponse;
import com.itam.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 强制修改初始密码拦截器：位于 JwtFilter 之后执行（SecurityConfig 中 addFilterAfter）。
 *
 * 规则：
 * 1) 若 SecurityContext 中无认证，或 principal 不是 JwtUserPrincipal（匿名/非 JWT），
 *    直接放行，交由 SecurityConfig 的 permitAll / authenticated 规则处理。
 * 2) 若 principal.isMustChangePassword() == false，放行。
 * 3) 若 principal 尚未修改初始密码，仅允许以下三个端点，其余一律返回 403：
 *    - POST /v1/auth/change-password
 *    - POST /v1/auth/logout
 *    - GET  /v1/auth/me
 *    返回结构统一的 ApiResponse（code=MUST_CHANGE_PASSWORD, message=请先修改初始密码）。
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MustChangePasswordFilter.class);

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST");
    private static final String CHANGE_PASSWORD = "/v1/auth/change-password";
    private static final String LOGOUT = "/v1/auth/logout";
    private static final String ME = "/v1/auth/me";

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            // 匿名 / 非 JWT 认证：交由 SecurityConfig 的 permitAll / authenticated 规则处理
            filterChain.doFilter(request, response);
            return;
        }
        if (!principal.isMustChangePassword()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 命中强制改密拦截：返回 403 + 结构化错误体
        log.debug("Blocked request for user [{}] who must change password: {} {}",
                principal.getUsername(), request.getMethod(), request.getServletPath());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ResultCode.MUST_CHANGE_PASSWORD, "请先修改初始密码")));
    }

    private boolean isAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (!ALLOWED_METHODS.contains(method)) {
            return false;
        }
        return switch (method) {
            case "POST" -> CHANGE_PASSWORD.equals(path) || LOGOUT.equals(path);
            case "GET" -> ME.equals(path);
            default -> false;
        };
    }
}
