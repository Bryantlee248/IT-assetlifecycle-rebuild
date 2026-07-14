package com.itam.metadata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 创建字段定义请求。
 * storageType 默认 jsonb；uniqueScope 默认 none；visible/editable 默认 true。
 * 若 uniqueScope != none，必须映射到热点物理列（否则发布被拒 422）。
 */
public record CreateFieldRequest(
        @NotBlank String fieldCode,
        @NotBlank String fieldName,
        @NotBlank String fieldType,
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
