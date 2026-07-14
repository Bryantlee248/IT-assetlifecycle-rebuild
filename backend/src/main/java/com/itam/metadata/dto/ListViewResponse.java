package com.itam.metadata.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 列表配置响应。schemaJson 为运行时列表列定义。
 */
public record ListViewResponse(
        UUID id,
        UUID tenantId,
        UUID assetTypeId,
        Map<String, Object> schemaJson,
        int version,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
