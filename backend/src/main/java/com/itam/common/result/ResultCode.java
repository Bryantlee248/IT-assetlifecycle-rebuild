package com.itam.common.result;

import lombok.Getter;

/**
 * 统一错误码。code=0 表示成功；非 0 为业务/系统错误。
 * HTTP 语义由 GlobalExceptionHandler 映射到标准状态码。
 *
 * MVP-1 新增：422/429 及资产/类型/字段冲突与资产不存在码（见 GlobalExceptionHandler.toHttpStatus）。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数错误"),
    UNAUTHENTICATED(40100, "未认证或登录已失效"),
    REFRESH_INVALID(40101, "Refresh Token 无效或已轮换"),
    NO_PERMISSION(40300, "无权限"),
    MUST_CHANGE_PASSWORD(40300, "请先修改初始密码"),
    NOT_FOUND(40400, "资源不存在"),
    // MVP-1：资产不存在（含跨租户统一 404）
    ASSET_NOT_FOUND(40401, "资产不存在或无权限"),
    CONFLICT(40900, "资源冲突"),
    // MVP-1：细分冲突码
    ASSET_NO_CONFLICT(40901, "资产编号已存在"),
    SERIAL_NO_CONFLICT(40902, "序列号已存在"),
    TYPE_CODE_CONFLICT(40903, "资产类型编码已存在"),
    FIELD_CODE_CONFLICT(40904, "字段编码已存在"),
    // MVP-1：业务规则违反（含字段唯一性拒绝）
    BUSINESS_RULE_VIOLATION(42200, "业务规则校验失败"),
    FIELD_UNIQUE_REJECTED(42201, "非热点字段不能声明唯一，请升格为热点字段"),
    // MVP-1：限流（预留）
    RATE_LIMITED(42900, "请求过于频繁，请稍后重试"),
    BUSINESS_ERROR(50000, "业务错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
