package com.itam.asset.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 资产详情响应。
 * 标准身份/引用字段为顶层属性；fields 为权限过滤后的动态字段集合（含物理热点列与扩展 JSONB），
 * 不可见字段已剔除、敏感字段已按 maskRule 脱敏。
 */
public record AssetResponse(
        UUID id,
        String assetNo,
        String assetName,
        String assetKind,
        UUID assetTypeId,
        String assetTypeName,
        String lifecycleStatus,
        String status,
        String sourceType,
        UUID ownerUserId,
        UUID ownerOrgId,
        UUID locationId,
        UUID costCenterId,
        UUID responsibleUserId,
        String createdAt,
        String updatedAt,
        Map<String, Object> fields) {
}
