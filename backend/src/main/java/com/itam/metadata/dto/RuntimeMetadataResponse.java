package com.itam.metadata.dto;

import com.itam.metadata.domain.FieldPermissionView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时元数据聚合响应：前端据此渲染动态表单/列表/筛选。
 * fieldPermissions 以 fieldCode 为键，提供当前角色对每个字段的可见/可编辑/脱敏/可导出。
 */
public record RuntimeMetadataResponse(
        AssetTypeResponse assetType,
        List<FieldDefinitionResponse> fields,
        Map<String, FieldPermissionView> fieldPermissions,
        Map<String, Object> formSchema,
        Map<String, Object> listView,
        Map<String, Object> searchSchema) {
}
