package com.itam.common.exception;

import com.itam.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。携带 ResultCode 与可选明细，由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
