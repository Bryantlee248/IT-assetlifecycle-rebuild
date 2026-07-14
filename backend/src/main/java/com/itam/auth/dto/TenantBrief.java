package com.itam.auth.dto;

import java.util.UUID;

public record TenantBrief(
        UUID id,
        String name,
        String code,
        String status
) {
}
