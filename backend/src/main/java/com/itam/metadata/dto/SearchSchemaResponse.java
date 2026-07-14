package com.itam.metadata.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 查询配置响应。schemaJson 为运行时筛选控件定义。
 */
public record SearchSchemaResponse(
        UUID id,
        UUID tenantId,
        UUID assetTypeId,
        Map<String, Object> schemaJson,
        int version,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
