package com.itam.metadata.dto;

import java.util.Map;
import java.util.UUID;

/**
 * 字段定义响应。
 */
public record FieldDefinitionResponse(
        UUID id,
        UUID tenantId,
        UUID assetTypeId,
        String fieldCode,
        String fieldName,
        String fieldType,
        String storageType,
        String physicalColumn,
        boolean required,
        String uniqueScope,
        Map<String, Object> defaultValue,
        Map<String, Object> validationRule,
        Map<String, Object> dataSource,
        boolean searchable,
        boolean sortable,
        boolean indexed,
        boolean visible,
        boolean editable,
        boolean sensitive,
        boolean encrypted,
        String maskRule,
        int sortOrder,
        String status,
        String createdAt,
        String updatedAt) {
}
