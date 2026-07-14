package com.itam.tenantadmin.dto;

import java.util.List;
import java.util.UUID;

public record OrgNode(
        UUID id,
        String name,
        String code,
        String type,
        int sort,
        String status,
        List<OrgNode> children
) {
}
