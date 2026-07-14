package com.itam.tenantadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateOrgRequest(
        UUID parentId,
        @NotBlank(message = "组织名称不能为空") String name,
        @NotBlank(message = "组织编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "编码仅含字母数字下划线") String code,
        String type,
        int sort
) {
}
