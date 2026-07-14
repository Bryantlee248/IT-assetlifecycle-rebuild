package com.itam.tenantadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,48}$", message = "编码仅含字母数字下划线，2-48 位") String code,
        @NotBlank(message = "角色名称不能为空") String name,
        String description
) {
}
