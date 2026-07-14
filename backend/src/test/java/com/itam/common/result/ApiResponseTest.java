package com.itam.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应信封测试：success/fail 构建器产出固定结构与非空 traceId。
 */
class ApiResponseTest {

    @Test
    void success_carries_data_and_trace() {
        ApiResponse<String> r = ApiResponse.success("hello");
        assertThat(r.getCode()).isZero();
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getTraceId()).isNotBlank();
    }

    @Test
    void success_without_data() {
        ApiResponse<Void> r = ApiResponse.success();
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isNull();
        assertThat(r.getTraceId()).isNotBlank();
    }

    @Test
    void fail_uses_code_message() {
        ApiResponse<Void> r = ApiResponse.fail(ResultCode.NO_PERMISSION);
        assertThat(r.getCode()).isEqualTo(40300);
        assertThat(r.getMessage()).isEqualTo("无权限");
        assertThat(r.getData()).isNull();
        assertThat(r.getTraceId()).isNotBlank();
    }

    @Test
    void fail_with_custom_message_overrides_default() {
        ApiResponse<Void> r = ApiResponse.fail(ResultCode.UNAUTHENTICATED, "用户名或密码错误");
        assertThat(r.getCode()).isEqualTo(40100);
        assertThat(r.getMessage()).isEqualTo("用户名或密码错误");
    }
}
