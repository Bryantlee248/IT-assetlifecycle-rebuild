package com.itam.metadata.application;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.domain.DynamicIndexService;
import com.itam.metadata.domain.MetadataValidator;
import com.itam.metadata.domain.UniqueScopeValidator;
import com.itam.metadata.dto.CreateFieldRequest;
import com.itam.metadata.dto.FieldDefinitionResponse;
import com.itam.metadata.dto.UpdateFieldRequest;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 字段定义应用服务：CRUD/启停 + 唯一性校验 + 动态索引。
 *
 * 关键约束：
 *  - field_code 在 (tenant, type) 内唯一 → 409 FIELD_CODE_CONFLICT
 *  - unique_scope != none 且非热点物理列 → 422 FIELD_UNIQUE_REJECTED（发布被拒）
 *  - 物理字段必须命中热点白名单（UniqueScopeValidator.validateStorage）
 *  - 唯一字段在发布事务内幂等创建部分唯一索引（DynamicIndexService）
 */
@Service
@RequiredArgsConstructor
public class FieldAppService {

    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final UniqueScopeValidator uniqueScopeValidator;
    private final MetadataValidator metadataValidator;
    private final DynamicIndexService dynamicIndexService;
    private final AuditLogService auditLogService;

    public List<FieldDefinitionResponse> listFields(UUID tenantId, UUID typeId) {
        ensureType(tenantId, typeId);
        return fieldDefinitionRepository.findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, typeId)
                .stream().map(MetadataMapper::toFieldResponse).toList();
    }

    public FieldDefinitionResponse createField(UUID tenantId, UUID userId, UUID typeId, CreateFieldRequest req) {
        ensureType(tenantId, typeId);
        if (fieldDefinitionRepository.existsByTenantIdAndAssetTypeIdAndFieldCode(tenantId, typeId, req.fieldCode())) {
            throw new BusinessException(ResultCode.FIELD_CODE_CONFLICT);
        }
        metadataValidator.validateFieldCreate(req);

        FieldDefinition fd = new FieldDefinition();
        fd.setTenantId(tenantId);
        fd.setAssetTypeId(typeId);
        fd.setFieldCode(req.fieldCode());
        fd.setFieldName(req.fieldName());
        fd.setFieldType(req.fieldType());
        fd.setStorageType(req.storageType() == null ? "jsonb" : req.storageType());
        fd.setPhysicalColumn(req.physicalColumn());
        fd.setRequired(req.required() != null && req.required());
        fd.setUniqueScope(req.uniqueScope() == null ? "none" : req.uniqueScope());
        fd.setDefaultValue(req.defaultValue());
        fd.setValidationRule(req.validationRule());
        fd.setDataSource(req.dataSource());
        fd.setSearchable(req.searchable() != null && req.searchable());
        fd.setSortable(req.sortable() != null && req.sortable());
        fd.setIndexed(req.indexed() != null && req.indexed());
        fd.setVisible(req.visible() == null || req.visible());
        fd.setEditable(req.editable() == null || req.editable());
        fd.setSensitive(req.sensitive() != null && req.sensitive());
        fd.setEncrypted(req.encrypted() != null && req.encrypted());
        fd.setMaskRule(req.maskRule());
        fd.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        fd.setStatus(req.status() == null ? "enabled" : req.status());
        fd.setCreatedBy(userId);
        fd.setUpdatedBy(userId);

        uniqueScopeValidator.validate(fd);
        uniqueScopeValidator.validateStorage(fd);
        ensureDynamicIndex(fd);

        FieldDefinition saved = fieldDefinitionRepository.save(fd);
        auditLogService.log("FIELD_CREATE", "FIELD", saved.getId().toString(),
                Map.of("fieldCode", saved.getFieldCode()));
        return MetadataMapper.toFieldResponse(saved);
    }

    public FieldDefinitionResponse updateField(UUID tenantId, UUID userId, UUID fieldId, UpdateFieldRequest req) {
        FieldDefinition fd = fieldDefinitionRepository.findByTenantIdAndId(tenantId, fieldId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        metadataValidator.validateFieldUpdate(fd, req);
        if (req.fieldName() != null) {
            fd.setFieldName(req.fieldName());
        }
        if (req.fieldType() != null) {
            fd.setFieldType(req.fieldType());
        }
        if (req.storageType() != null) {
            fd.setStorageType(req.storageType());
        }
        if (req.physicalColumn() != null) {
            fd.setPhysicalColumn(req.physicalColumn());
        }
        if (req.required() != null) {
            fd.setRequired(req.required());
        }
        if (req.uniqueScope() != null) {
            fd.setUniqueScope(req.uniqueScope());
        }
        if (req.defaultValue() != null) {
            fd.setDefaultValue(req.defaultValue());
        }
        if (req.validationRule() != null) {
            fd.setValidationRule(req.validationRule());
        }
        if (req.dataSource() != null) {
            fd.setDataSource(req.dataSource());
        }
        if (req.searchable() != null) {
            fd.setSearchable(req.searchable());
        }
        if (req.sortable() != null) {
            fd.setSortable(req.sortable());
        }
        if (req.indexed() != null) {
            fd.setIndexed(req.indexed());
        }
        if (req.visible() != null) {
            fd.setVisible(req.visible());
        }
        if (req.editable() != null) {
            fd.setEditable(req.editable());
        }
        if (req.sensitive() != null) {
            fd.setSensitive(req.sensitive());
        }
        if (req.encrypted() != null) {
            fd.setEncrypted(req.encrypted());
        }
        if (req.maskRule() != null) {
            fd.setMaskRule(req.maskRule());
        }
        if (req.sortOrder() != null) {
            fd.setSortOrder(req.sortOrder());
        }
        if (req.status() != null) {
            fd.setStatus(req.status());
        }
        fd.setUpdatedBy(userId);

        uniqueScopeValidator.validate(fd);
        uniqueScopeValidator.validateStorage(fd);
        ensureDynamicIndex(fd);

        FieldDefinition saved = fieldDefinitionRepository.save(fd);
        auditLogService.log("FIELD_UPDATE", "FIELD", fieldId.toString(),
                Map.of("fieldCode", saved.getFieldCode()));
        return MetadataMapper.toFieldResponse(saved);
    }

    public void setFieldStatus(UUID tenantId, UUID userId, UUID fieldId, boolean enabled) {
        FieldDefinition fd = fieldDefinitionRepository.findByTenantIdAndId(tenantId, fieldId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        fd.setStatus(enabled ? "enabled" : "disabled");
        fd.setUpdatedBy(userId);
        fieldDefinitionRepository.save(fd);
        auditLogService.log("FIELD_STATUS", "FIELD", fieldId.toString(), Map.of("enabled", enabled));
    }

    private void ensureDynamicIndex(FieldDefinition fd) {
        if (!"none".equalsIgnoreCase(fd.getUniqueScope())) {
            dynamicIndexService.ensurePartialUniqueIndex(fd.getPhysicalColumn(), fd.getUniqueScope());
        }
    }

    private void ensureType(UUID tenantId, UUID typeId) {
        assetTypeRepository.findByTenantIdAndId(tenantId, typeId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
    }
}
