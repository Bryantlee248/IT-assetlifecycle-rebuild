package com.itam.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResultCode 枚举契约测试：code 与 message 固定，供统一错误响应与全局异常映射使用。
 */
class ResultCodeTest {

    @Test
    void success_code_and_message_fixed() {
        assertThat(ResultCode.SUCCESS.getCode()).isZero();
        assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("success");
    }

    @Test
    void auth_related_codes() {
        assertThat(ResultCode.UNAUTHENTICATED.getCode()).isEqualTo(40100);
        assertThat(ResultCode.REFRESH_INVALID.getCode()).isEqualTo(40101);
        assertThat(ResultCode.NO_PERMISSION.getCode()).isEqualTo(40300);
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(40400);
        assertThat(ResultCode.CONFLICT.getCode()).isEqualTo(40900);
        assertThat(ResultCode.PARAM_ERROR.getCode()).isEqualTo(40000);
        assertThat(ResultCode.BUSINESS_ERROR.getCode()).isEqualTo(50000);
    }

    @Test
    void every_code_distinct() {
        int[] codes = {
                ResultCode.SUCCESS.getCode(), ResultCode.PARAM_ERROR.getCode(),
                ResultCode.UNAUTHENTICATED.getCode(), ResultCode.REFRESH_INVALID.getCode(),
                ResultCode.NO_PERMISSION.getCode(), ResultCode.NOT_FOUND.getCode(),
                ResultCode.CONFLICT.getCode(), ResultCode.BUSINESS_ERROR.getCode()
        };
        assertThat(codes).doesNotHaveDuplicates();
    }
}
