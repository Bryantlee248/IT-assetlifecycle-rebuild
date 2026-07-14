package com.itam.asset.dto;

import java.util.UUID;

/**
 * 资产关系响应。
 */
public record AssetRelationDto(
        UUID id,
        UUID sourceAssetId,
        UUID targetAssetId,
        String relationType,
        String description,
        String createdAt) {
}
