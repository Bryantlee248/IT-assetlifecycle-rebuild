package com.itam.tenantadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, message = "初始密码至少 8 位") String password,
        String displayName,
        String email,
        String phone,
        UUID roleId,
        String status
) {
}
