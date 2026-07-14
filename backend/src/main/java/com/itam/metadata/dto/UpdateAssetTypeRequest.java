package com.itam.metadata.dto;

import java.util.UUID;

/**
 * 更新资产类型请求（全部可选，未传字段不覆盖）。type_code 不允许修改。
 */
public record UpdateAssetTypeRequest(
        UUID parentId,
        String typeName,
        String assetKind,
        UUID lifecycleTemplateId,
        String icon,
        Boolean enabled,
        Integer sortOrder) {
}
