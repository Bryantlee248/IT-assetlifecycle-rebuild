package com.itam.tenantadmin.dto;

import java.util.UUID;

public record UpdateUserRequest(
        String displayName,
        String email,
        String phone,
        UUID roleId,
        String status
) {
}
