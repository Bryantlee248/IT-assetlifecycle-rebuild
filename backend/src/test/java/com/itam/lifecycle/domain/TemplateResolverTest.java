package com.itam.lifecycle.domain;

import com.itam.asset.entity.Asset;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.repository.LifecycleTemplateRepository;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.repository.AssetTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板解析单元测试（Mockito，无 Spring 上下文）。
 * 覆盖优先级分支（asset_type -> 启用模板）、禁用/缺失/软删模板回退、兜底列表过滤与稳定排序。
 *
 * <p>关键契约：repository 方法名已显式带 {@code DeletedFalse}/{@code OrderBy}，
 * 测试通过 {@code when(...).thenReturn(...)} 控制返回列表（不含软删项）来证明
 * 服务只拿到过滤后的数据，并验证解析器依赖的是带排序的新方法。
 */
@ExtendWith(MockitoExtension.class)
class TemplateResolverTest {

    @Mock
    private AssetTypeRepository assetTypeRepository;

    @Mock
    private LifecycleTemplateRepository lifecycleTemplateRepository;

    @InjectMocks
    private TemplateResolver resolver;

    private Asset asset(UUID typeId, String assetKind) {
        Asset a = new Asset();
        a.setAssetKind(assetKind);
        a.setAssetTypeId(typeId);
        a.setLifecycleStatus("planned");
        return a;
    }

    private AssetType type(UUID templateId) {
        AssetType t = new AssetType();
        t.setLifecycleTemplateId(templateId);
        return t;
    }

    private LifecycleTemplate template(UUID id, String assetKind, boolean enabled) {
        LifecycleTemplate t = new LifecycleTemplate();
        t.setId(id);
        t.setName("tpl-" + id.toString().substring(0, 4));
        t.setAssetKind(assetKind);
        t.setEnabled(enabled);
        t.setDeleted(false);
        return t;
    }

    /** 绑定模板命中的兜底查询不会被触发，标记 lenient 以免 strict-stubs 报未使用桩。 */
    private void stubFallbackLenient(LifecycleTemplate def) {
        lenient().when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(def));
    }

    @Test
    void priority_returnsEnabledBoundTemplate_overAssetKindDefault() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID boundId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");
        AssetType type = type(boundId);
        LifecycleTemplate bound = template(boundId, "tangible", true);
        LifecycleTemplate def = template(defaultId, "tangible", true);

        when(assetTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(type));
        when(lifecycleTemplateRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, boundId))
                .thenReturn(Optional.of(bound));
        stubFallbackLenient(def);

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(boundId, result.getId());
    }

    @Test
    void boundDisabled_fallsBackToEnabledAssetKindDefault() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID disabledId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");
        AssetType type = type(disabledId);
        LifecycleTemplate disabled = template(disabledId, "tangible", false);
        LifecycleTemplate def = template(defaultId, "tangible", true);

        when(assetTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(type));
        when(lifecycleTemplateRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, disabledId))
                .thenReturn(Optional.of(disabled));
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of(def));

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(defaultId, result.getId());
    }

    @Test
    void boundMissing_fallsBackToAssetKindDefault() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");
        AssetType type = type(missingId);
        LifecycleTemplate def = template(defaultId, "tangible", true);

        when(assetTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(type));
        when(lifecycleTemplateRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, missingId))
                .thenReturn(Optional.empty());
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of(def));

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(defaultId, result.getId());
    }

    @Test
    void noTemplateAtAll_throwsBusinessException() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");

        when(assetTypeRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> resolver.resolve(tenantId, asset));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertEquals("资产未关联生命周期模板", ex.getMessage());
    }

    /**
     * 绑定模板被软删：优先级分支查询（AndDeletedFalse）返回 empty，模拟查询层已排除软删行，
     * 解析器应回退到 asset_kind 默认启用模板。
     */
    @Test
    void boundDeleted_fallsBackToAssetKindDefault() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID boundId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");
        AssetType type = type(boundId);
        LifecycleTemplate def = template(defaultId, "tangible", true);

        when(assetTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(type));
        when(lifecycleTemplateRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, boundId))
                .thenReturn(Optional.empty());
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of(def));

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(defaultId, result.getId());
    }

    /**
     * 兜底列表只返回未删模板（不放入 deleted 项），证明服务只拿到过滤后数据。
     */
    @Test
    void fallbackList_excludesDeletedTemplate() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");

        // 软删模板不放入 stub 列表，模拟查询层已滤除。
        LifecycleTemplate deleted = template(UUID.randomUUID(), "tangible", true);
        deleted.setDeleted(true);
        LifecycleTemplate alive = template(defaultId, "tangible", true);

        when(assetTypeRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of(alive));

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(defaultId, result.getId());
    }

    /**
     * 多个启用默认模板：解析器依赖带 OrderByCreatedAtAsc 的查询，返回列表中 createdAt 最早者。
     */
    @Test
    void multipleEnabledDefaults_returnsEarliestByCreatedAt() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID earlierId = UUID.randomUUID();
        UUID laterId = UUID.randomUUID();
        Asset asset = asset(typeId, "tangible");

        LifecycleTemplate earlier = template(earlierId, "tangible", true);
        earlier.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        LifecycleTemplate later = template(laterId, "tangible", true);
        later.setCreatedAt(Instant.parse("2024-02-01T00:00:00Z"));

        when(assetTypeRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
        when(lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible"))
                .thenReturn(List.of(earlier, later));

        LifecycleTemplate result = resolver.resolve(tenantId, asset);
        assertEquals(earlierId, result.getId());
        verify(lifecycleTemplateRepository)
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, "tangible");
    }
}
