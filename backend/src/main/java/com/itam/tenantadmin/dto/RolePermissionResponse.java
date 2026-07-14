package com.itam.tenantadmin.dto;

import java.util.List;
import java.util.UUID;

public record RolePermissionResponse(
        UUID roleId,
        List<String> permissions
) {
}
