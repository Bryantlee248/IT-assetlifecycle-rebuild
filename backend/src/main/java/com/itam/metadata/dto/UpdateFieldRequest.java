package com.itam.metadata.dto;

import java.util.Map;

/**
 * 更新字段定义请求。field_code / asset_type_id 不可修改。
 */
public record UpdateFieldRequest(
        String fieldName,
        String fieldType,
        String storageType,
        String physicalColumn,
        Boolean required,
        String uniqueScope,
        Map<String, Object> defaultValue,
        Map<String, Object> validationRule,
        Map<String, Object> dataSource,
        Boolean searchable,
        Boolean sortable,
        Boolean indexed,
        Boolean visible,
        Boolean editable,
        Boolean sensitive,
        Boolean encrypted,
        String maskRule,
        Integer sortOrder,
        String status) {
}
