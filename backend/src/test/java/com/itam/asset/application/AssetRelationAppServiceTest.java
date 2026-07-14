package com.itam.asset.application;

import com.itam.asset.dto.AssetRelationDto;
import com.itam.asset.dto.CreateRelationRequest;
import com.itam.asset.entity.Asset;
import com.itam.asset.entity.AssetRelation;
import com.itam.asset.repository.AssetRelationRepository;
import com.itam.asset.repository.AssetRepository;
import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.asset.domain.AssetAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4 资产关系类型枚举约束 + 自环拒绝 + 跨租户校验 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AssetRelationAppServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock AssetRelationRepository assetRelationRepository;
    @Mock AssetAssembler assetAssembler;
    @Mock AuditLogService auditLogService;

    private AssetRelationAppService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssetRelationAppService(assetRepository, assetRelationRepository, assetAssembler, auditLogService);
    }

    private void stubSourceExists() {
        Asset a = new Asset();
        a.setTenantId(tenantId);
        a.setAssetTypeId(UUID.randomUUID());
        a.setAssetNo("A-1");
        a.setAssetName("源资产");
        when(assetRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(java.util.Optional.of(a));
    }

    private void stubTargetExists() {
        Asset t = new Asset();
        t.setTenantId(tenantId);
        when(assetRepository.findByTenantIdAndId(tenantId, targetId)).thenReturn(java.util.Optional.of(t));
    }

    // ---- 合法关系类型放行 ----
    @Test
    void valid_relation_type_succeeds() {
        stubSourceExists();
        stubTargetExists();
        when(assetRelationRepository.existsByTenantIdAndSourceAssetIdAndTargetAssetIdAndRelationType(any(), any(), any(), any()))
                .thenReturn(false);
        when(assetRelationRepository.save(any(AssetRelation.class))).thenAnswer(i -> i.getArgument(0));
        when(assetAssembler.toRelationDto(any())).thenReturn(null);

        service.create(tenantId, userId, assetId, new CreateRelationRequest(targetId, "installed_on", "x"));
        verify(assetRelationRepository, times(1)).save(any(AssetRelation.class));
    }

    // ---- 非法关系类型 → 422 ----
    @Test
    void invalid_relation_type_rejected() {
        stubSourceExists();
        stubTargetExists();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(tenantId, userId, assetId, new CreateRelationRequest(targetId, "bad_type", "x")));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.BUSINESS_RULE_VIOLATION);
        verify(assetRelationRepository, times(0)).save(any());
    }

    // ---- 自环（源==目标）→ 422 ----
    @Test
    void self_loop_rejected() {
        stubSourceExists();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(tenantId, userId, assetId, new CreateRelationRequest(assetId, "installed_on", "x")));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.BUSINESS_RULE_VIOLATION);
        verify(assetRelationRepository, times(0)).save(any());
    }

    // ---- 重复关系 → 422 ----
    @Test
    void duplicate_relation_rejected() {
        stubSourceExists();
        stubTargetExists();
        when(assetRelationRepository.existsByTenantIdAndSourceAssetIdAndTargetAssetIdAndRelationType(any(), any(), any(), any()))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(tenantId, userId, assetId, new CreateRelationRequest(targetId, "installed_on", "x")));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.BUSINESS_RULE_VIOLATION);
        verify(assetRelationRepository, times(0)).save(any());
    }

    // ---- 跨租户目标 → 404 ----
    @Test
    void cross_tenant_target_not_found() {
        stubSourceExists();
        when(assetRepository.findByTenantIdAndId(tenantId, targetId)).thenReturn(java.util.Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(tenantId, userId, assetId, new CreateRelationRequest(targetId, "installed_on", "x")));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.ASSET_NOT_FOUND);
        verify(assetRelationRepository, times(0)).save(any());
    }
}
