package com.itam.common.exception;

import com.itam.common.result.ApiResponse;
import com.itam.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：将异常统一转换为 ApiResponse 信封，并映射为对应 HTTP 状态码。
 * 认证/授权相关的过滤器级异常（401/403）由 security 包内的 EntryPoint/AccessDeniedHandler 处理。
 *
 * MVP-1 新增：BUSINESS_RULE_VIOLATION / FIELD_UNIQUE_REJECTED -> 422，RATE_LIMITED -> 429。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("BusinessException: {} | {} | {}", ex.getResultCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(toHttpStatus(ex.getResultCode()))
                .body(ApiResponse.fail(ex.getResultCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        ApiResponse<Map<String, String>> body = ApiResponse.fail(ResultCode.PARAM_ERROR, "参数校验失败", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ResultCode.NO_PERMISSION, ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ResultCode.UNAUTHENTICATED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ResultCode.BUSINESS_ERROR, "系统异常，请稍后重试"));
    }

    private HttpStatus toHttpStatus(ResultCode code) {
        return switch (code) {
            case PARAM_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED, REFRESH_INVALID -> HttpStatus.UNAUTHORIZED;
            case NO_PERMISSION, MUST_CHANGE_PASSWORD -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, ASSET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, ASSET_NO_CONFLICT, SERIAL_NO_CONFLICT,
                 TYPE_CODE_CONFLICT, FIELD_CODE_CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE_VIOLATION, FIELD_UNIQUE_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
