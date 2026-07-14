package com.itam.security;

/**
 * 用户类型：平台用户（无租户上下文）或租户用户（含租户上下文）。
 */
public enum UserType {
    PLATFORM,
    TENANT
}
