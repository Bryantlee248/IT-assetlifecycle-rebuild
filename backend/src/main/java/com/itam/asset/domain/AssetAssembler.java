package com.itam.asset.domain;

import com.itam.asset.domain.FieldCryptoService;
import com.itam.asset.dto.AssetListItem;
import com.itam.asset.dto.AssetRelationDto;
import com.itam.asset.dto.AssetResponse;
import com.itam.asset.entity.Asset;
import com.itam.asset.entity.AssetRelation;
import com.itam.common.util.Iso;
import com.itam.metadata.domain.FieldPermissionService;
import com.itam.metadata.domain.FieldPermissionView;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 资产实体 ↔ 响应装配，套用 FieldPermissionService 做可见性过滤与脱敏。
 *
 * 规则：
 *  - 身份/引用字段为响应顶层属性（始终可见，非敏感）
 *  - 动态字段（来自字段定义）进入 fields Map：不可见字段剔除、敏感字段按 maskRule 脱敏
 *  - 物理热点列与 JSONB 扩展字段统一按字段定义读取
 */
@Service
public class AssetAssembler {

    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final FieldPermissionService fieldPermissionService;
    private final AssetTypeRepository assetTypeRepository;
    private final FieldCryptoService fieldCryptoService;

    public AssetAssembler(FieldDefinitionRepository fieldDefinitionRepository,
                          FieldPermissionService fieldPermissionService,
                          AssetTypeRepository assetTypeRepository,
                          FieldCryptoService fieldCryptoService) {
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.fieldPermissionService = fieldPermissionService;
        this.assetTypeRepository = assetTypeRepository;
        this.fieldCryptoService = fieldCryptoService;
    }

    public AssetResponse toResponse(Asset asset, UUID tenantId, String roleCode) {
        UUID atId = asset.getAssetTypeId();
        String atName = assetTypeRepository.findByTenantIdAndId(tenantId, atId)
                .map(AssetType::getTypeName).orElse(null);
        List<FieldDefinition> defs = fieldDefinitionRepository
                .findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, atId);
        Map<String, Object> fields = buildFields(asset, tenantId, roleCode, defs);
        // P0-2：顶层固定字段同样按字段权限过滤（不可见字段返回 null，不泄露）。
        Set<String> vis = visibleTopFields(tenantId, roleCode, atId);
        return new AssetResponse(
                asset.getId(), asset.getAssetNo(),
                vis.contains("asset_name") ? asset.getAssetName() : null,
                asset.getAssetKind(),
                atId, atName, asset.getLifecycleStatus(), asset.getStatus(), asset.getSourceType(),
                vis.contains("owner_user_id") ? asset.getOwnerUserId() : null,
                vis.contains("owner_org_id") ? asset.getOwnerOrgId() : null,
                vis.contains("location_id") ? asset.getLocationId() : null,
                vis.contains("cost_center_id") ? asset.getCostCenterId() : null,
                vis.contains("responsible_user_id") ? asset.getResponsibleUserId() : null,
                Iso.of(asset.getCreatedAt()), Iso.of(asset.getUpdatedAt()), fields);
    }

    public AssetListItem toListItem(Asset asset, UUID tenantId, String roleCode) {
        UUID atId = asset.getAssetTypeId();
        String atName = assetTypeRepository.findByTenantIdAndId(tenantId, atId)
                .map(AssetType::getTypeName).orElse(null);
        List<FieldDefinition> defs = fieldDefinitionRepository
                .findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, atId);
        Map<String, Object> fields = buildFields(asset, tenantId, roleCode, defs);
        // P0-2：顶层固定字段同样按字段权限过滤。
        Set<String> vis = visibleTopFields(tenantId, roleCode, atId);
        return new AssetListItem(asset.getId(), asset.getAssetNo(),
                vis.contains("asset_name") ? asset.getAssetName() : null,
                atId, atName, asset.getLifecycleStatus(), asset.getStatus(), fields);
    }

    /** 受权限管控的顶层热点物理列（身份/系统列恒可见，不在此列）。 */
    private static final Set<String> TOP_HOTSPOT = Set.of(
            "asset_name", "owner_user_id", "owner_org_id", "location_id", "cost_center_id",
            "responsible_user_id", "serial_no", "brand", "model", "vendor",
            "warranty_end_date", "license_end_date");

    private Set<String> visibleTopFields(UUID tenantId, String roleCode, UUID atId) {
        Set<String> visible = new java.util.HashSet<>();
        for (String col : TOP_HOTSPOT) {
            if (fieldPermissionService.resolve(tenantId, roleCode, atId, col).visible()) {
                visible.add(col);
            }
        }
        return visible;
    }

    public AssetRelationDto toRelationDto(AssetRelation r) {
        return new AssetRelationDto(r.getId(), r.getSourceAssetId(), r.getTargetAssetId(),
                r.getRelationType(), r.getDescription(), Iso.of(r.getCreatedAt()));
    }

    private Map<String, Object> buildFields(Asset asset, UUID tenantId, String roleCode,
                                           List<FieldDefinition> defs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldDefinition fd : defs) {
            String code = fd.getFieldCode();
            FieldPermissionView perm = fieldPermissionService.resolveByCode(tenantId, roleCode, fd, code);
            if (!perm.visible()) {
                continue;
            }
            Object value = readValue(asset, fd);
            if (value == null) {
                continue;
            }
            if (perm.masked() && perm.maskRule() != null) {
                value = applyMask(value, perm.maskRule());
            }
            fields.put(code, value);
        }
        return fields;
    }

    private Object readValue(Asset asset, FieldDefinition fd) {
        if ("physical".equalsIgnoreCase(fd.getStorageType()) && fd.getPhysicalColumn() != null) {
            return asset.getPhysicalValue(fd.getPhysicalColumn());
        }
        Object value = asset.getAttributes().get(fd.getFieldCode());
        if ("encrypted".equalsIgnoreCase(fd.getStorageType()) && value instanceof String s) {
            return fieldCryptoService.decrypt(s);
        }
        return value;
    }

    private Object applyMask(Object value, String maskRule) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return switch (maskRule) {
            case "last4" -> "***" + (s.length() <= 4 ? s : s.substring(s.length() - 4));
            case "first4" -> (s.length() <= 4 ? s : s.substring(0, 4)) + "***";
            case "middle" -> maskMiddle(s);
            default -> "***";
        };
    }

    private String maskMiddle(String s) {
        if (s.length() <= 2) {
            return "***";
        }
        int keep = Math.max(1, s.length() / 4);
        return s.substring(0, keep) + "***" + s.substring(s.length() - keep);
    }
}
