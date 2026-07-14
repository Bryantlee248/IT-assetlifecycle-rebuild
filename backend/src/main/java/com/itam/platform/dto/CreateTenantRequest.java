package com.itam.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
        @NotBlank(message = "租户名称不能为空") String name,
        @NotBlank(message = "租户编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "编码仅含字母数字下划线，2-32 位") String code,
        String description
) {
}
