package com.itam.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String code,
        String status,
        String description,
        Instant createdAt
) {
}
