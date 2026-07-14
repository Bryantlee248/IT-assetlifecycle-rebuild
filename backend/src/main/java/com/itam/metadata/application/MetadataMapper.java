package com.itam.metadata.application;

import com.itam.common.util.Iso;
import com.itam.metadata.dto.AssetTypeResponse;
import com.itam.metadata.dto.FieldDefinitionResponse;
import com.itam.metadata.dto.FormSchemaResponse;
import com.itam.metadata.dto.ListViewResponse;
import com.itam.metadata.dto.SearchSchemaResponse;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.entity.FormSchema;
import com.itam.metadata.entity.ListViewSchema;
import com.itam.metadata.entity.SearchSchema;

import java.util.Map;

/**
 * 元数据实体 → DTO 映射（静态方法，无状态）。
 */
public final class MetadataMapper {

    private MetadataMapper() {
    }

    public static AssetTypeResponse toTypeResponse(AssetType e) {
        return new AssetTypeResponse(
                e.getId(), e.getTenantId(), e.getParentId(), e.getTypeCode(), e.getTypeName(),
                e.getAssetKind(), e.getLifecycleTemplateId(), e.getIcon(), e.isEnabled(),
                e.getSortOrder(), Iso.of(e.getCreatedAt()), Iso.of(e.getUpdatedAt()));
    }

    public static FieldDefinitionResponse toFieldResponse(FieldDefinition e) {
        return new FieldDefinitionResponse(
                e.getId(), e.getTenantId(), e.getAssetTypeId(), e.getFieldCode(), e.getFieldName(),
                e.getFieldType(), e.getStorageType(), e.getPhysicalColumn(), e.isRequired(),
                e.getUniqueScope(), e.getDefaultValue(), e.getValidationRule(), e.getDataSource(),
                e.isSearchable(), e.isSortable(), e.isIndexed(), e.isVisible(), e.isEditable(),
                e.isSensitive(), e.isEncrypted(), e.getMaskRule(), e.getSortOrder(), e.getStatus(),
                Iso.of(e.getCreatedAt()), Iso.of(e.getUpdatedAt()));
    }

    public static FormSchemaResponse toFormResponse(FormSchema e) {
        return new FormSchemaResponse(
                e.getId(), e.getTenantId(), e.getAssetTypeId(), e.getSchemaJson(),
                e.getVersion(), e.isEnabled(), Iso.of(e.getCreatedAt()), Iso.of(e.getUpdatedAt()));
    }

    public static ListViewResponse toListResponse(ListViewSchema e) {
        return new ListViewResponse(
                e.getId(), e.getTenantId(), e.getAssetTypeId(), e.getSchemaJson(),
                e.getVersion(), e.isEnabled(), Iso.of(e.getCreatedAt()), Iso.of(e.getUpdatedAt()));
    }

    public static SearchSchemaResponse toSearchResponse(SearchSchema e) {
        return new SearchSchemaResponse(
                e.getId(), e.getTenantId(), e.getAssetTypeId(), e.getSchemaJson(),
                e.getVersion(), e.isEnabled(), Iso.of(e.getCreatedAt()), Iso.of(e.getUpdatedAt()));
    }

    /** 空 JSONB 的便捷构造（避免 null）。 */
    public static Map<String, Object> emptyJson() {
        return Map.of();
    }
}
