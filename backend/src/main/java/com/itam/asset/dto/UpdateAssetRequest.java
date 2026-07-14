package com.itam.asset.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 更新资产请求。lifecycleStatus 在编辑时被忽略（生命周期由 MVP-2 动作控制）。
 */
public record UpdateAssetRequest(
        String assetName,
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
