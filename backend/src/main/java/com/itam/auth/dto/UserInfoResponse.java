package com.itam.auth.dto;

import java.util.List;
import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String phone,
        String userType,
        UUID tenantId,
        boolean mustChangePassword,
        List<String> permissions
) {
}
