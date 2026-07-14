package com.itam.metadata.domain;

import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.FieldDefinitionRepository;
import com.itam.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 字段权限默认引擎（MVP-1 内存矩阵实现，不依赖 field_permission_rules 表内数据）。
 *
 * 解析顺序：
 *  ① 字段基础属性（visible / editable / sensitive / maskRule）
 *  ② 角色基线矩阵（PRD §5 代表角色：tenant_admin / asset_admin / asset_operator / auditor / asset_user）
 *  ③ 输出 { visible, editable, masked, exportable, maskRule }
 *
 * 约定：
 *  - 角色以"角色码"（来自 JWT claims 的 roles）传入，而非角色 UUID，避免额外查库。
 *  - 字段定义缺失（如固定物理列）时按"非系统、非敏感"合成基线，便于统一管控固定列。
 *
 * 敏感字段按角色的处理（PRD §5）：
 *  - tenant_admin：可见（按授权），可编辑，不脱敏，可导出
 *  - asset_admin ：可见，可编辑，脱敏，可导出
 *  - asset_operator：可见，脱敏，不可编辑敏感字段，敏感不可导出
 *  - auditor      ：可见，脱敏，不可编辑，敏感不可导出
 *  - asset_user   ：隐藏（敏感字段对使用人不可见），不可编辑，不可导出
 */
@Service
public class FieldPermissionService {

    /** 系统字段：租户角色一律不可编辑（仅供展示/系统写入）。 */
    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "tenant_id", "asset_type_id", "lifecycle_status", "status",
            "source_type", "sync_source", "metadata_version",
            "created_at", "created_by", "updated_at", "updated_by", "deleted");

    private final FieldDefinitionRepository fieldDefinitionRepository;

    public FieldPermissionService(FieldDefinitionRepository fieldDefinitionRepository) {
        this.fieldDefinitionRepository = fieldDefinitionRepository;
    }

    /**
     * 按字段编码解析权限（字段定义可能不存在，如固定物理列）。
     */
    public FieldPermissionView resolve(UUID tenantId, String roleCode, UUID assetTypeId, String fieldCode) {
        FieldDefinition fd = fieldDefinitionRepository
                .findByTenantIdAndAssetTypeIdAndFieldCode(tenantId, assetTypeId, fieldCode)
                .orElse(null);
        return resolveInternal(roleCode, fd, fieldCode);
    }

    /**
     * 批量解析时复用已加载的 FieldDefinition。
     */
    public FieldPermissionView resolveByCode(UUID tenantId, String roleCode,
                                             FieldDefinition fd, String fieldCode) {
        return resolveInternal(roleCode, fd, fieldCode);
    }

    private FieldPermissionView resolveInternal(String roleCode, FieldDefinition fd, String fieldCode) {
        Baseline b = resolveBaseline(roleCode);
        boolean sensitive = fd != null && fd.isSensitive();
        boolean system = isSystem(fieldCode);
        boolean baseVisible = fd == null ? true : fd.isVisible();
        boolean baseEditable = fd == null ? true : fd.isEditable();

        boolean visible = computeVisible(b, system, sensitive, baseVisible);
        boolean editable = computeEditable(b, system, sensitive, baseEditable);
        boolean masked = sensitive && b.maskSensitive && visible;
        boolean exportable = sensitive ? b.exportableSensitive : b.exportable;
        String maskRule = fd != null ? fd.getMaskRule() : null;
        return new FieldPermissionView(visible, editable, masked, exportable, maskRule);
    }

    private boolean computeVisible(Baseline b, boolean system, boolean sensitive, boolean baseVisible) {
        if (system) {
            return true; // 系统字段可见（但不可编辑）
        }
        if (sensitive) {
            return b != Baseline.ASSET_USER; // asset_user 对敏感字段隐藏
        }
        return baseVisible;
    }

    private boolean computeEditable(Baseline b, boolean system, boolean sensitive, boolean baseEditable) {
        if (system) {
            return false;
        }
        if (sensitive) {
            return baseEditable && b.editableNonSystem && b.editSensitive;
        }
        return baseEditable && b.editableNonSystem;
    }

    private Baseline resolveBaseline(String roleCode) {
        if (roleCode == null) {
            return Baseline.OTHER;
        }
        return switch (roleCode) {
            case "tenant_admin" -> Baseline.TENANT_ADMIN;
            case "asset_admin" -> Baseline.ASSET_ADMIN;
            case "asset_operator" -> Baseline.ASSET_OPERATOR;
            case "auditor" -> Baseline.AUDITOR;
            case "asset_user" -> Baseline.ASSET_USER;
            default -> Baseline.OTHER;
        };
    }

    private boolean isSystem(String fieldCode) {
        return fieldCode != null && SYSTEM_FIELDS.contains(fieldCode);
    }

    /**
     * 角色基线。
     *  - editableNonSystem：非系统字段是否默认可编辑
     *  - editSensitive：敏感字段是否可编辑（仅管理员）
     *  - maskSensitive：敏感字段是否脱敏展示
     *  - exportable：非敏感字段是否可导出
     *  - exportableSensitive：敏感字段是否可导出
     */
    private enum Baseline {
        TENANT_ADMIN(true, true, false, true, true),
        ASSET_ADMIN(true, true, true, true, true),
        ASSET_OPERATOR(true, false, true, false, false),
        AUDITOR(false, false, true, false, false),
        ASSET_USER(false, false, true, false, false),
        OTHER(false, false, true, false, false);

        private final boolean editableNonSystem;
        private final boolean editSensitive;
        private final boolean maskSensitive;
        private final boolean exportable;
        private final boolean exportableSensitive;

        Baseline(boolean editableNonSystem, boolean editSensitive, boolean maskSensitive,
                 boolean exportable, boolean exportableSensitive) {
            this.editableNonSystem = editableNonSystem;
            this.editSensitive = editSensitive;
            this.maskSensitive = maskSensitive;
            this.exportable = exportable;
            this.exportableSensitive = exportableSensitive;
        }
    }
}
