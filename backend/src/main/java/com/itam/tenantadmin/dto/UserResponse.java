package com.itam.tenantadmin.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String phone,
        String status,
        UUID roleId,
        String roleName,
        UUID tenantId
) {
}
