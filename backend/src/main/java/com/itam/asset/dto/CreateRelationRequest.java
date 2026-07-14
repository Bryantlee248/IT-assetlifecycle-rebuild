package com.itam.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建资产关系请求。
 */
public record CreateRelationRequest(
        @NotNull UUID targetAssetId,
        @NotBlank String relationType,
        String description) {
}
