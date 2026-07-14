package com.itam.metadata.application;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.dto.AssetTypeNode;
import com.itam.metadata.dto.AssetTypeResponse;
import com.itam.metadata.dto.CreateAssetTypeRequest;
import com.itam.metadata.dto.UpdateAssetTypeRequest;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.repository.AssetTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 资产类型应用服务：类型树/CRUD/启停 + 审计。
 * 所有写操作强制 tenant_id 与审计；跨租户访问经 findByTenantIdAndId 空结果 → 404。
 */
@Service
@RequiredArgsConstructor
public class AssetTypeAppService {

    private final AssetTypeRepository assetTypeRepository;
    private final AuditLogService auditLogService;

    public List<AssetTypeNode> tree(UUID tenantId) {
        List<AssetType> all = assetTypeRepository.findByTenantIdOrderBySortAsc(tenantId);
        Map<UUID, List<AssetType>> childrenMap = new LinkedHashMap<>();
        List<AssetType> roots = new ArrayList<>();
        for (AssetType t : all) {
            if (t.getParentId() == null) {
                roots.add(t);
            } else {
                childrenMap.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        List<AssetTypeNode> result = new ArrayList<>();
        for (AssetType r : roots) {
            result.add(toNode(r, childrenMap));
        }
        return result;
    }

    public AssetTypeResponse create(UUID tenantId, UUID userId, CreateAssetTypeRequest req) {
        if (assetTypeRepository.existsByTenantIdAndTypeCode(tenantId, req.typeCode())) {
            throw new BusinessException(ResultCode.TYPE_CODE_CONFLICT);
        }
        AssetType e = new AssetType();
        e.setTenantId(tenantId);
        e.setParentId(req.parentId());
        e.setTypeCode(req.typeCode());
        e.setTypeName(req.typeName());
        e.setAssetKind(req.assetKind());
        e.setLifecycleTemplateId(req.lifecycleTemplateId());
        e.setIcon(req.icon());
        e.setEnabled(req.enabled() == null || req.enabled());
        e.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        e.setCreatedBy(userId);
        e.setUpdatedBy(userId);
        AssetType saved = assetTypeRepository.save(e);
        auditLogService.log("ASSET_TYPE_CREATE", "ASSET_TYPE", saved.getId().toString(),
                Map.of("typeCode", saved.getTypeCode()));
        return MetadataMapper.toTypeResponse(saved);
    }

    public AssetTypeResponse update(UUID tenantId, UUID userId, UUID id, UpdateAssetTypeRequest req) {
        AssetType e = assetTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        if (req.parentId() != null) {
            e.setParentId(req.parentId());
        }
        if (req.typeName() != null) {
            e.setTypeName(req.typeName());
        }
        if (req.assetKind() != null) {
            e.setAssetKind(req.assetKind());
        }
        if (req.lifecycleTemplateId() != null) {
            e.setLifecycleTemplateId(req.lifecycleTemplateId());
        }
        if (req.icon() != null) {
            e.setIcon(req.icon());
        }
        if (req.enabled() != null) {
            e.setEnabled(req.enabled());
        }
        if (req.sortOrder() != null) {
            e.setSortOrder(req.sortOrder());
        }
        e.setUpdatedBy(userId);
        AssetType saved = assetTypeRepository.save(e);
        auditLogService.log("ASSET_TYPE_UPDATE", "ASSET_TYPE", id.toString(),
                Map.of("typeCode", saved.getTypeCode()));
        return MetadataMapper.toTypeResponse(saved);
    }

    public void setStatus(UUID tenantId, UUID userId, UUID id, boolean enabled) {
        AssetType e = assetTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        e.setEnabled(enabled);
        e.setUpdatedBy(userId);
        assetTypeRepository.save(e);
        auditLogService.log("ASSET_TYPE_STATUS", "ASSET_TYPE", id.toString(), Map.of("enabled", enabled));
    }

    private AssetTypeNode toNode(AssetType t, Map<UUID, List<AssetType>> childrenMap) {
        List<AssetTypeNode> kids = new ArrayList<>();
        for (AssetType c : childrenMap.getOrDefault(t.getId(), List.of())) {
            kids.add(toNode(c, childrenMap));
        }
        return new AssetTypeNode(t.getId(), t.getTypeCode(), t.getTypeName(), t.getAssetKind(),
                t.getIcon(), t.isEnabled(), t.getSortOrder(), kids);
    }
}
