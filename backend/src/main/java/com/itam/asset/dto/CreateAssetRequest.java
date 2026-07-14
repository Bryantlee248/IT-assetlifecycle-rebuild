package com.itam.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * 创建资产请求。
 * 身份字段 assetName/assetNo 必填；其余物理/扩展字段经运行时元数据映射到物理列或 attributes。
 * lifecycleStatus 由后端固定为 planned，请求中携带的值被忽略。
 */
public record CreateAssetRequest(
        @NotNull UUID assetTypeId,
        @NotBlank String assetName,
        @NotBlank String assetNo,
        UUID ownerUserId,
        UUID ownerOrgId,
        UUID locationId,
        UUID costCenterId,
        UUID responsibleUserId,
        String serialNo,
        String brand,
        String model,
        String vendor,
        String warrantyEndDate,
        String licenseEndDate,
        String lifecycleStatus,
        Map<String, Object> attributes) {
}
