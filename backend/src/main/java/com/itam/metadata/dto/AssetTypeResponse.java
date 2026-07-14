package com.itam.metadata.dto;

import java.util.UUID;

/**
 * 资产类型响应。
 */
public record AssetTypeResponse(
        UUID id,
        UUID tenantId,
        UUID parentId,
        String typeCode,
        String typeName,
        String assetKind,
        UUID lifecycleTemplateId,
        String icon,
        boolean enabled,
        int sortOrder,
        String createdAt,
        String updatedAt) {
}
