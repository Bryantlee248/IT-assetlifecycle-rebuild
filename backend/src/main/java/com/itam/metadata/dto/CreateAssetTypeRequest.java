package com.itam.metadata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建资产类型请求。type_code 在租户内唯一（部分唯一索引兜底 409）。
 */
public record CreateAssetTypeRequest(
        UUID parentId,
        @NotBlank String typeCode,
        @NotBlank String typeName,
        @NotBlank String assetKind,
        UUID lifecycleTemplateId,
        String icon,
        Boolean enabled,
        Integer sortOrder) {
}
