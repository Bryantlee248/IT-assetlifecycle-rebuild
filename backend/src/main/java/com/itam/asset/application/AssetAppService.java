package com.itam.asset.application;

import com.itam.asset.domain.AssetAssembler;
import com.itam.asset.domain.AssetFieldMappingService;
import com.itam.asset.domain.AssetUniqueValidator;
import com.itam.metadata.domain.FieldPermissionService;
import com.itam.metadata.domain.FieldPermissionView;
import com.itam.asset.dto.AssetListItem;
import com.itam.asset.dto.AssetQuery;
import com.itam.asset.dto.AssetResponse;
import com.itam.asset.dto.CreateAssetRequest;
import com.itam.asset.dto.UpdateAssetRequest;
import com.itam.asset.entity.Asset;
import com.itam.asset.repository.AssetRepository;
import com.itam.asset.repository.AssetSpecifications;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.PageResult;
import com.itam.common.result.ResultCode;
import com.itam.asset.domain.FieldCryptoService;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import com.itam.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产应用服务：CRUD/列表 + 租户隔离 + 固定 planned + 字段映射 + 审计。
 *
 * 关键约束：
 *  - 创建 lifecycleStatus 固定 planned，忽略请求中的值
 *  - 编辑忽略请求中的 lifecycleStatus
 *  - 租户隔离：所有查询经 findByTenantIdAndId（@Where 软删），跨租户 → ASSET_NOT_FOUND(404)
 *  - asset_no / serial_no 重复预检 409，并兜底捕获部分唯一索引冲突
 *  - 所有写操作审计
 */
@Service
@RequiredArgsConstructor
public class AssetAppService {

    private final AssetRepository assetRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetFieldMappingService assetFieldMappingService;
    private final AssetUniqueValidator assetUniqueValidator;
    private final AssetAssembler assetAssembler;
    private final AuditLogService auditLogService;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final FieldCryptoService fieldCryptoService;
    private final FieldPermissionService fieldPermissionService;

    public AssetResponse create(UUID tenantId, UUID userId, String roleCode, CreateAssetRequest req) {
        AssetType at = assetTypeRepository.findByTenantIdAndId(tenantId, req.assetTypeId())
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        assetUniqueValidator.preCheck(tenantId, req.assetNo(), req.serialNo());

        Asset asset = new Asset();
        asset.setTenantId(tenantId);
        asset.setAssetTypeId(req.assetTypeId());
        asset.setAssetKind(at.getAssetKind());
        asset.setAssetName(req.assetName());
        asset.setAssetNo(req.assetNo());
        asset.setStatus("active");
        asset.setSourceType("manual");
        asset.setLifecycleStatus(Asset.LIFECYCLE_STATUS_PLANNED); // 忽略请求 lifecycleStatus

        Map<String, Object> values = toValueMap(req.ownerUserId(), req.ownerOrgId(), req.locationId(),
                req.costCenterId(), req.responsibleUserId(), req.serialNo(), req.brand(),
                req.model(), req.vendor(), req.warrantyEndDate(), req.licenseEndDate(), req.attributes());
        // P0-1：写路径强制字段权限——visible=false / editable=false 的字段拒绝写入（后端权威）。
        Map<String, Object> requested = new LinkedHashMap<>(values);
        requested.put("asset_name", req.assetName());
        requested.put("asset_no", req.assetNo());
        enforceWritePermission(tenantId, roleCode, req.assetTypeId(), requested);
        AssetFieldMappingService.SplitResult split = assetFieldMappingService.split(values);
        split.physical().forEach(asset::setPhysicalValue);
        asset.setAttributes(split.attributes());
        encryptFields(tenantId, req.assetTypeId(), split.attributes());
        asset.setCreatedBy(userId);
        asset.setUpdatedBy(userId);

        Asset saved = saveCatchConflict(asset);
        auditLogService.log("ASSET_CREATE", "ASSET", saved.getId().toString(),
                Map.of("assetNo", saved.getAssetNo()));
        return assetAssembler.toResponse(saved, tenantId, roleCode);
    }

    public AssetResponse update(UUID tenantId, UUID userId, String roleCode, UUID id, UpdateAssetRequest req) {
        Asset asset = assetRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        if (req.assetName() != null) {
            asset.setAssetName(req.assetName());
        }
        // 忽略 req.lifecycleStatus()

        Map<String, Object> values = toValueMap(req.ownerUserId(), req.ownerOrgId(), req.locationId(),
                req.costCenterId(), req.responsibleUserId(), req.serialNo(), req.brand(),
                req.model(), req.vendor(), req.warrantyEndDate(), req.licenseEndDate(), req.attributes());
        // P0-1：写路径强制字段权限——visible=false / editable=false 的字段拒绝写入（后端权威）。
        Map<String, Object> requested = new LinkedHashMap<>(values);
        if (req.assetName() != null) {
            requested.put("asset_name", req.assetName());
        }
        enforceWritePermission(tenantId, roleCode, asset.getAssetTypeId(), requested);
        AssetFieldMappingService.SplitResult split = assetFieldMappingService.split(values);
        split.physical().forEach(asset::setPhysicalValue);

        Map<String, Object> attrs = new LinkedHashMap<>(asset.getAttributes());
        attrs.putAll(split.attributes());
        asset.setAttributes(attrs);
        encryptFields(tenantId, asset.getAssetTypeId(), attrs);

        if (req.serialNo() != null && !req.serialNo().equals(asset.getSerialNo())
                && assetRepository.existsByTenantIdAndSerialNo(tenantId, req.serialNo())) {
            throw new BusinessException(ResultCode.SERIAL_NO_CONFLICT);
        }
        asset.setUpdatedBy(userId);

        Asset saved = saveCatchConflict(asset);
        auditLogService.log("ASSET_UPDATE", "ASSET", id.toString(), Map.of("assetNo", saved.getAssetNo()));
        return assetAssembler.toResponse(saved, tenantId, roleCode);
    }

    public AssetResponse get(UUID tenantId, UUID userId, String roleCode, UUID id) {
        Asset asset = assetRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        // P0-2：数据范围——asset_user 仅可查看本人/责任人相关资产。
        enforceDataScope(roleCode, userId, asset);
        return assetAssembler.toResponse(asset, tenantId, roleCode);
    }

    public PageResult<AssetListItem> list(UUID tenantId, UUID userId, String roleCode, AssetQuery q) {
        int size = Math.min(q.getSize() <= 0 ? 20 : q.getSize(), 200);
        int page = q.getPage() <= 0 ? 1 : q.getPage();
        Sort sort = parseSort(q.getSort());
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        // P0-2：注入数据范围（asset_user 仅本人/责任人相关资产）。
        q.setDataScopeUserId(userId != null ? userId.toString() : null);
        q.setDataScopeRole(roleCode);
        Specification<Asset> spec = AssetSpecifications.byQuery(tenantId, q);
        Page<Asset> pageResult = assetRepository.findAll(spec, pageable);
        List<AssetListItem> items = pageResult.getContent().stream()
                .map(a -> assetAssembler.toListItem(a, tenantId, roleCode)).toList();
        return PageResult.of(page, size, pageResult.getTotalElements(), items);
    }

    public void delete(UUID tenantId, UUID userId, UUID id) {
        Asset asset = assetRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        asset.setDeleted(true); // 软删：部分唯一索引 WHERE deleted=false 自动释放，允许同编号重建
        asset.setUpdatedBy(userId);
        assetRepository.save(asset);
        auditLogService.log("ASSET_DELETE", "ASSET", id.toString(), Map.of("assetNo", asset.getAssetNo()));
    }

    private Asset saveCatchConflict(Asset asset) {
        try {
            return assetRepository.save(asset);
        } catch (DataIntegrityViolationException ex) {
            RuntimeException translated = assetUniqueValidator.translateConflict(ex);
            if (translated instanceof BusinessException be) {
                throw be;
            }
            throw ex;
        }
    }

    private Map<String, Object> toValueMap(UUID ownerUserId, UUID ownerOrgId, UUID locationId,
                                           UUID costCenterId, UUID responsibleUserId, String serialNo,
                                           String brand, String model, String vendor,
                                           String warrantyEndDate, String licenseEndDate,
                                           Map<String, Object> attributes) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("owner_user_id", ownerUserId);
        values.put("owner_org_id", ownerOrgId);
        values.put("location_id", locationId);
        values.put("cost_center_id", costCenterId);
        values.put("responsible_user_id", responsibleUserId);
        values.put("serial_no", serialNo);
        values.put("brand", brand);
        values.put("model", model);
        values.put("vendor", vendor);
        values.put("warranty_end_date", warrantyEndDate);
        values.put("license_end_date", licenseEndDate);
        if (attributes != null) {
            values.putAll(attributes);
        }
        return values;
    }

    /**
     * P0-1：写路径字段权限强制。遍历请求中实际尝试写入的字段，
     * 凡可见性=false 或 可编辑性=false 的字段一律拒绝（后端权威，禁止静默覆盖）。
     */
    private void enforceWritePermission(UUID tenantId, String roleCode, UUID assetTypeId,
                                         Map<String, Object> requested) {
        if (roleCode == null) {
            return; // 无角色信息时不强制（鉴权由 SecurityContext 保证）
        }
        for (Map.Entry<String, Object> e : requested.entrySet()) {
            if (e.getValue() == null) {
                continue; // 未尝试写入该字段
            }
            FieldPermissionView perm = fieldPermissionService.resolve(tenantId, roleCode, assetTypeId, e.getKey());
            if (!perm.visible() || !perm.editable()) {
                throw new BusinessException(ResultCode.NO_PERMISSION,
                        "字段无写入权限: " + e.getKey());
            }
        }
    }

    /**
     * P0-2：数据范围强制。asset_user 仅可访问本人/责任人相关资产，越权访问按 404 处理（与跨租户一致）。
     */
    private void enforceDataScope(String roleCode, UUID userId, Asset asset) {
        if (!"asset_user".equals(roleCode) || userId == null) {
            return;
        }
        boolean owned = userId.equals(asset.getOwnerUserId());
        boolean responsible = userId.equals(asset.getResponsibleUserId());
        if (!owned && !responsible) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND);
        }
    }

    /**
     * 对 storage_type=encrypted 的字段值做 AES 加密后落库（已加密值跳过，避免重复加密）。
     */
    private void encryptFields(UUID tenantId, UUID assetTypeId, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        List<FieldDefinition> defs = fieldDefinitionRepository
                .findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, assetTypeId);
        for (FieldDefinition fd : defs) {
            if (!"encrypted".equalsIgnoreCase(fd.getStorageType())) {
                continue;
            }
            Object v = attributes.get(fd.getFieldCode());
            if (v instanceof String s && !s.startsWith("enc:")) {
                attributes.put(fd.getFieldCode(), fieldCryptoService.encrypt(s));
            }
        }
    }

    private Sort parseSort(String sort) {
        if (sort != null && !sort.isBlank() && sort.contains(",")) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            if (isValidSortField(field)) {
                return Sort.by(dir, field);
            }
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private boolean isValidSortField(String field) {
        return List.of("assetNo", "assetName", "lifecycleStatus", "status",
                "createdAt", "updatedAt", "warrantyEndDate", "licenseEndDate",
                "brand", "model", "vendor", "serialNo").contains(field);
    }
}
