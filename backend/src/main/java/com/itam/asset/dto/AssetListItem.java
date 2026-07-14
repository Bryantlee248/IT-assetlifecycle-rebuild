package com.itam.asset.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 资产列表项响应（轻量）。字段权限已应用（剔除不可见列、脱敏敏感列）。
 */
public record AssetListItem(
        UUID id,
        String assetNo,
        String assetName,
        UUID assetTypeId,
        String assetTypeName,
        String lifecycleStatus,
        String status,
        Map<String, Object> fields) {
}
