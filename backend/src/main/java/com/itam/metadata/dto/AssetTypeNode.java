package com.itam.metadata.dto;

import java.util.List;
import java.util.UUID;

/**
 * 资产类型树节点（含子节点）。
 */
public record AssetTypeNode(
        UUID id,
        String typeCode,
        String typeName,
        String assetKind,
        String icon,
        boolean enabled,
        int sortOrder,
        List<AssetTypeNode> children) {
}
