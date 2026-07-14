package com.itam.common.exception;

import com.itam.common.result.ApiResponse;
import com.itam.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器单元测试（无 Spring 上下文、不依赖 PG/Redis）。
 * 验证四类异常响应均携带非空 traceId（统一由 ApiResponse.fail 生成）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    @Test
    void business_exception_has_trace_id() {
        ResponseEntity<ApiResponse<Void>> r = handler.handleBusiness(
                new BusinessException(ResultCode.NOT_FOUND, "资源不存在"), request());
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().getTraceId()).isNotBlank();
    }

    @Test
    void validation_exception_has_trace_id() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.addError(new FieldError("obj", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, br);
        ResponseEntity<ApiResponse<java.util.Map<String, String>>> r = handler.handleValidation(ex);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().getTraceId()).isNotBlank();
        assertThat(r.getBody().getData()).containsKey("name");
    }

    @Test
    void access_denied_has_trace_id() {
        ResponseEntity<ApiResponse<Void>> r = handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().getTraceId()).isNotBlank();
    }

    @Test
    void authentication_exception_has_trace_id() {
        BadCredentialsException ex = new BadCredentialsException("bad credentials");
        ResponseEntity<ApiResponse<Void>> r = handler.handleAuthentication(ex);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().getTraceId()).isNotBlank();
    }
}
