package com.itam.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 统一响应信封。所有接口返回结构保持一致：
 * { code, message, data, traceId }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .data(data)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode, String message) {
        return ApiResponse.<T>builder()
                .code(resultCode.getCode())
                .message(message != null ? message : resultCode.getMessage())
                .data(null)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode, String message, T data) {
        return ApiResponse.<T>builder()
                .code(resultCode.getCode())
                .message(message != null ? message : resultCode.getMessage())
                .data(data)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode) {
        return fail(resultCode, null);
    }
}
