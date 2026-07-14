package com.itam.metadata.application;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.dto.FormSchemaResponse;
import com.itam.metadata.dto.ListViewResponse;
import com.itam.metadata.dto.SearchSchemaResponse;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FormSchema;
import com.itam.metadata.entity.ListViewSchema;
import com.itam.metadata.entity.SearchSchema;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FormSchemaRepository;
import com.itam.metadata.repository.ListViewSchemaRepository;
import com.itam.metadata.repository.SearchSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 表单/列表/查询配置应用服务。每个资产类型至多一份启用配置，PUT 时版本自增。
 */
@Service
@RequiredArgsConstructor
public class SchemaAppService {

    private final FormSchemaRepository formSchemaRepository;
    private final ListViewSchemaRepository listViewSchemaRepository;
    private final SearchSchemaRepository searchSchemaRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AuditLogService auditLogService;

    // ===== 表单配置 =====
    public FormSchemaResponse getFormSchema(UUID tenantId, UUID typeId) {
        ensureType(tenantId, typeId);
        return formSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(MetadataMapper::toFormResponse).orElse(null);
    }

    public FormSchemaResponse putFormSchema(UUID tenantId, UUID userId, UUID typeId, Map<String, Object> schemaJson) {
        ensureType(tenantId, typeId);
        FormSchema e = formSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId).orElseGet(() -> {
            FormSchema n = new FormSchema();
            n.setTenantId(tenantId);
            n.setAssetTypeId(typeId);
            n.setCreatedBy(userId);
            return n;
        });
        e.setSchemaJson(schemaJson == null ? Map.of() : schemaJson);
        e.setVersion(e.getVersion() + 1);
        e.setEnabled(true);
        e.setUpdatedBy(userId);
        FormSchema saved = formSchemaRepository.save(e);
        auditLogService.log("FORM_SCHEMA_SAVE", "SCHEMA", saved.getId().toString(),
                Map.of("assetTypeId", typeId.toString()));
        return MetadataMapper.toFormResponse(saved);
    }

    // ===== 列表配置 =====
    public ListViewResponse getListView(UUID tenantId, UUID typeId) {
        ensureType(tenantId, typeId);
        return listViewSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(MetadataMapper::toListResponse).orElse(null);
    }

    public ListViewResponse putListView(UUID tenantId, UUID userId, UUID typeId, Map<String, Object> schemaJson) {
        ensureType(tenantId, typeId);
        ListViewSchema e = listViewSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId).orElseGet(() -> {
            ListViewSchema n = new ListViewSchema();
            n.setTenantId(tenantId);
            n.setAssetTypeId(typeId);
            n.setCreatedBy(userId);
            return n;
        });
        e.setSchemaJson(schemaJson == null ? Map.of() : schemaJson);
        e.setVersion(e.getVersion() + 1);
        e.setEnabled(true);
        e.setUpdatedBy(userId);
        ListViewSchema saved = listViewSchemaRepository.save(e);
        auditLogService.log("LIST_VIEW_SAVE", "SCHEMA", saved.getId().toString(),
                Map.of("assetTypeId", typeId.toString()));
        return MetadataMapper.toListResponse(saved);
    }

    // ===== 查询配置 =====
    public SearchSchemaResponse getSearchSchema(UUID tenantId, UUID typeId) {
        ensureType(tenantId, typeId);
        return searchSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(MetadataMapper::toSearchResponse).orElse(null);
    }

    public SearchSchemaResponse putSearchSchema(UUID tenantId, UUID userId, UUID typeId, Map<String, Object> schemaJson) {
        ensureType(tenantId, typeId);
        SearchSchema e = searchSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId).orElseGet(() -> {
            SearchSchema n = new SearchSchema();
            n.setTenantId(tenantId);
            n.setAssetTypeId(typeId);
            n.setCreatedBy(userId);
            return n;
        });
        e.setSchemaJson(schemaJson == null ? Map.of() : schemaJson);
        e.setVersion(e.getVersion() + 1);
        e.setEnabled(true);
        e.setUpdatedBy(userId);
        SearchSchema saved = searchSchemaRepository.save(e);
        auditLogService.log("SEARCH_SCHEMA_SAVE", "SCHEMA", saved.getId().toString(),
                Map.of("assetTypeId", typeId.toString()));
        return MetadataMapper.toSearchResponse(saved);
    }

    private void ensureType(UUID tenantId, UUID typeId) {
        assetTypeRepository.findByTenantIdAndId(tenantId, typeId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
    }
}
