package com.itam.tenantadmin.dto;

import java.util.UUID;

public record UpdateOrgRequest(
        UUID parentId,
        String name,
        String code,
        String type,
        Integer sort,
        String status
) {
}
