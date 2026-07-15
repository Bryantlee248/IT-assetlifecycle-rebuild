package com.itam.lifecycle.domain;

import com.itam.asset.entity.Asset;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.repository.LifecycleTemplateRepository;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.repository.AssetTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 模板解析：优先 asset_type_id -> asset_types.lifecycle_template_id；
 * 兜底按 asset_kind 匹配该租户默认模板（同 kind 仅一个默认模板）。
 * 无模板 -> BusinessException(BUSINESS_RULE_VIOLATION, "资产未关联生命周期模板")。
 */
@Service
@RequiredArgsConstructor
public class TemplateResolver {

    private final AssetTypeRepository assetTypeRepository;
    private final LifecycleTemplateRepository lifecycleTemplateRepository;

    public LifecycleTemplate resolve(UUID tenantId, Asset asset) {
        // 1) 优先：asset_type_id -> lifecycle_template_id（仅当模板存在且启用）
        AssetType type = assetTypeRepository.findByTenantIdAndId(tenantId, asset.getAssetTypeId()).orElse(null);
        if (type != null && type.getLifecycleTemplateId() != null) {
            LifecycleTemplate t = lifecycleTemplateRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, type.getLifecycleTemplateId()).orElse(null);
            // 模板存在且启用(enabled=true)才走优先级分支；否则落入默认分支。
            if (t != null && t.isEnabled()) {
                return t;
            }
        }
        // 2) 兜底：按 asset_kind 匹配该租户"启用中"的默认模板（enabled=true）
        List<LifecycleTemplate> templates =
                lifecycleTemplateRepository
                        .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(
                                tenantId, asset.getAssetKind());
        if (templates != null && !templates.isEmpty()) {
            return templates.get(0);
        }
        throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "资产未关联生命周期模板");
    }
}
