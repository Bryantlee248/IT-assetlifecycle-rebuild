package com.itam.metadata.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 表单配置响应。schemaJson 为运行时表单渲染所需的 JSON 结构。
 */
public record FormSchemaResponse(
        UUID id,
        UUID tenantId,
        UUID assetTypeId,
        Map<String, Object> schemaJson,
        int version,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
