package com.itam.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String userType,
        boolean mustChangePassword,
        UUID tenantId,
        String username,
        String displayName
) {
}
