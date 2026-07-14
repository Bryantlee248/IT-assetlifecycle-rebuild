package com.itam.asset.application;

import com.itam.asset.domain.AssetAssembler;
import com.itam.asset.dto.AssetRelationDto;
import com.itam.asset.dto.CreateRelationRequest;
import com.itam.asset.entity.Asset;
import com.itam.asset.entity.AssetRelation;
import com.itam.asset.repository.AssetRelationRepository;
import com.itam.asset.repository.AssetRepository;
import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 资产关系应用服务：关系 CRUD + 审计。
 * 源/目标资产必须属于当前租户（跨租户 → 404）；关系软删。
 */
@Service
@RequiredArgsConstructor
public class AssetRelationAppService {

    private final AssetRepository assetRepository;
    private final AssetRelationRepository assetRelationRepository;
    private final AssetAssembler assetAssembler;
    private final AuditLogService auditLogService;

    public List<AssetRelationDto> list(UUID tenantId, UUID assetId) {
        ensureAsset(tenantId, assetId);
        return assetRelationRepository.findByTenantIdAndSourceAssetId(tenantId, assetId).stream()
                .map(assetAssembler::toRelationDto).toList();
    }

    public AssetRelationDto create(UUID tenantId, UUID userId, UUID assetId, CreateRelationRequest req) {
        ensureAsset(tenantId, assetId);
        ensureAsset(tenantId, req.targetAssetId()); // 目标必须同租户
        // P0-4：关系类型枚举约束 + 自环拒绝。
        validateRelationType(req.relationType());
        if (assetId.equals(req.targetAssetId())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "关系源与目标资产不能相同");
        }
        if (assetRelationRepository.existsByTenantIdAndSourceAssetIdAndTargetAssetIdAndRelationType(
                tenantId, assetId, req.targetAssetId(), req.relationType())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "该关系已存在");
        }
        AssetRelation r = new AssetRelation();
        r.setTenantId(tenantId);
        r.setSourceAssetId(assetId);
        r.setTargetAssetId(req.targetAssetId());
        r.setRelationType(req.relationType());
        r.setDescription(req.description());
        AssetRelation saved = assetRelationRepository.save(r);
        auditLogService.log("ASSET_RELATION_CREATE", "ASSET_RELATION", saved.getId().toString(),
                Map.of("sourceAssetId", assetId.toString(), "targetAssetId", req.targetAssetId().toString()));
        return assetAssembler.toRelationDto(saved);
    }

    public void delete(UUID tenantId, UUID userId, UUID assetId, UUID relationId) {
        AssetRelation r = assetRelationRepository.findByTenantIdAndId(tenantId, relationId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        if (!r.getSourceAssetId().equals(assetId)) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND);
        }
        r.setDeleted(true);
        assetRelationRepository.save(r);
        auditLogService.log("ASSET_RELATION_DELETE", "ASSET_RELATION", relationId.toString(),
                Map.of("sourceAssetId", assetId.toString()));
    }

    private void ensureAsset(UUID tenantId, UUID assetId) {
        assetRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
    }

    /** P0-4：允许的资产关系类型白名单（与 V5 迁移中的 CHECK 约束一致）。 */
    private static final java.util.Set<String> ALLOWED_RELATION_TYPES = java.util.Set.of(
            "installed_on", "binds_to", "depends_on", "located_in", "uses");

    private void validateRelationType(String relationType) {
        if (relationType == null || !ALLOWED_RELATION_TYPES.contains(relationType)) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "非法的关系类型: " + relationType);
        }
    }
}
