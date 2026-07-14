package com.itam.asset.application;

import com.itam.asset.domain.AssetAssembler;
import com.itam.asset.domain.AssetFieldMappingService;
import com.itam.asset.domain.AssetUniqueValidator;
import com.itam.asset.domain.FieldCryptoService;
import com.itam.asset.dto.AssetResponse;
import com.itam.asset.dto.CreateAssetRequest;
import com.itam.asset.dto.UpdateAssetRequest;
import com.itam.asset.entity.Asset;
import com.itam.metadata.entity.AssetType;
import com.itam.asset.repository.AssetRepository;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.domain.FieldPermissionService;
import com.itam.metadata.domain.FieldPermissionView;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import com.itam.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1 写路径字段权限 + P0-5 敏感加密 + 数据范围（asset_user 越权）单元测试。
 * 纯 Mockito 切片，不依赖数据库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetAppServiceWritePermissionTest {

    @Mock AssetRepository assetRepository;
    @Mock AssetTypeRepository assetTypeRepository;
    @Mock AssetFieldMappingService assetFieldMappingService;
    @Mock AssetUniqueValidator assetUniqueValidator;
    @Mock AssetAssembler assetAssembler;
    @Mock AuditLogService auditLogService;
    @Mock FieldDefinitionRepository fieldDefinitionRepository;
    @Mock FieldCryptoService fieldCryptoService;
    @Mock FieldPermissionService fieldPermissionService;

    private AssetAppService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID assetTypeId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    private final FieldPermissionView EDITABLE = new FieldPermissionView(true, true, false, true, null);
    private final FieldPermissionView READONLY = new FieldPermissionView(true, false, false, false, null);

    @BeforeEach
    void setUp() {
        service = new AssetAppService(assetRepository, assetTypeRepository, assetFieldMappingService,
                assetUniqueValidator, assetAssembler, auditLogService, fieldDefinitionRepository,
                fieldCryptoService, fieldPermissionService);
        // encryptFields 依赖字段定义；默认返回空列表避免 NPE，敏感字段测试单独覆盖。
        when(fieldDefinitionRepository.findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, assetTypeId))
                .thenReturn(List.of());
    }

    private AssetType assetType() {
        AssetType at = new AssetType();
        at.setAssetKind("DEVICE");
        at.setTypeName("服务器");
        return at;
    }

    private CreateAssetRequest buildCreate(String lifecycleStatus, Map<String, Object> attributes) {
        return new CreateAssetRequest(assetTypeId, "测试资产", "A-001", null, null, null, null, null,
                null, null, null, null, null, null, lifecycleStatus, attributes);
    }

    // ---- P0-1: asset_admin 写路径放行 ----
    @Test
    void asset_admin_create_allowed() {
        when(assetTypeRepository.findByTenantIdAndId(tenantId, assetTypeId)).thenReturn(Optional.of(assetType()));
        when(assetFieldMappingService.split(any())).thenReturn(new AssetFieldMappingService.SplitResult(new LinkedHashMap<>(), new LinkedHashMap<>()));
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(EDITABLE);
        when(fieldCryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));
        when(assetRepository.save(any(Asset.class))).thenAnswer(i -> i.getArgument(0));
        when(assetAssembler.toResponse(any(), any(), any())).thenReturn(null);

        service.create(tenantId, userId, "asset_admin", buildCreate("in_use", new LinkedHashMap<>()));
        verify(assetRepository).save(any(Asset.class));
    }

    // ---- P0-1: auditor 写路径拒绝（后端权威）----
    @Test
    void auditor_create_rejected() {
        when(assetTypeRepository.findByTenantIdAndId(tenantId, assetTypeId)).thenReturn(Optional.of(assetType()));
        when(assetFieldMappingService.split(any())).thenReturn(new AssetFieldMappingService.SplitResult(new LinkedHashMap<>(), new LinkedHashMap<>()));
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(READONLY);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(tenantId, userId, "auditor", buildCreate(null, new LinkedHashMap<>())));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.NO_PERMISSION);
        verify(assetRepository, never()).save(any());
    }

    // ---- P0-1: lifecycleStatus 创建固定 planned（忽略请求值）----
    @Test
    void lifecycle_status_fixed_to_planned_on_create() {
        when(assetTypeRepository.findByTenantIdAndId(tenantId, assetTypeId)).thenReturn(Optional.of(assetType()));
        when(assetFieldMappingService.split(any())).thenReturn(new AssetFieldMappingService.SplitResult(new LinkedHashMap<>(), new LinkedHashMap<>()));
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(EDITABLE);
        when(fieldCryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));
        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
        when(assetAssembler.toResponse(any(), any(), any())).thenReturn(null);

        service.create(tenantId, userId, "asset_admin", buildCreate("in_use", new LinkedHashMap<>()));
        assertThat(cap.getValue().getLifecycleStatus()).isEqualTo(Asset.LIFECYCLE_STATUS_PLANNED);
        assertThat(cap.getValue().getLifecycleStatus()).isNotEqualTo("in_use");
    }

    // ---- P0-1: lifecycleStatus 编辑被忽略 ----
    @Test
    void lifecycle_status_ignored_on_update() {
        Asset existing = new Asset();
        existing.setTenantId(tenantId);
        existing.setAssetTypeId(assetTypeId);
        existing.setAssetNo("A-001");
        existing.setAssetName("原名称");
        existing.setLifecycleStatus("planned");
        existing.setAttributes(new LinkedHashMap<>());
        when(assetRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(existing));
        when(assetFieldMappingService.split(any())).thenReturn(new AssetFieldMappingService.SplitResult(new LinkedHashMap<>(), new LinkedHashMap<>()));
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(EDITABLE);
        when(fieldCryptoService.encrypt(any())).thenAnswer(i -> "enc:" + i.getArgument(0));
        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
        when(assetAssembler.toResponse(any(), any(), any())).thenReturn(null);

        UpdateAssetRequest req = new UpdateAssetRequest(null, null, null, null, null, null, null,
                null, null, null, null, null, "in_use", null);
        service.update(tenantId, userId, "asset_admin", assetId, req);
        assertThat(cap.getValue().getLifecycleStatus()).isEqualTo("planned");
    }

    // ---- P0-5: 敏感字段加密落库 + 审计详情无明文 ----
    @Test
    void sensitive_field_encrypted_and_audit_no_plaintext() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("license_key", "LIC-12345");
        when(assetTypeRepository.findByTenantIdAndId(tenantId, assetTypeId)).thenReturn(Optional.of(assetType()));
        when(assetFieldMappingService.split(any())).thenAnswer(i -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("license_key", "LIC-12345");
            return new AssetFieldMappingService.SplitResult(new LinkedHashMap<>(), a);
        });
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(EDITABLE);
        when(fieldCryptoService.encrypt(any())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        // P0-5：encryptFields 依赖字段定义识别 encrypted 字段，必须 stub。
        FieldDefinition licenseDef = new FieldDefinition();
        licenseDef.setFieldCode("license_key");
        licenseDef.setStorageType("encrypted");
        when(fieldDefinitionRepository.findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, assetTypeId))
                .thenReturn(List.of(licenseDef));
        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        when(assetRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
        when(assetAssembler.toResponse(any(), any(), any())).thenReturn(null);

        service.create(tenantId, userId, "asset_admin", buildCreate(null, attrs));
        assertThat((String) cap.getValue().getAttributes().get("license_key")).startsWith("enc:");

        ArgumentCaptor<Map<String, Object>> detailCap = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).log(eq("ASSET_CREATE"), eq("ASSET"), any(), detailCap.capture());
        assertThat(detailCap.getValue()).doesNotContainKey("license_key");
        assertThat(detailCap.getValue().values()).doesNotContain("LIC-12345");
    }

    // ---- P0-2: 数据范围（asset_user 仅访问本人/责任人资产，越权按 404）----
    @Test
    void asset_user_cannot_access_others_asset() {
        Asset other = new Asset();
        other.setTenantId(tenantId);
        other.setAssetTypeId(assetTypeId);
        other.setAssetNo("A-999");
        other.setAssetName("他人资产");
        other.setOwnerUserId(UUID.randomUUID());
        other.setResponsibleUserId(UUID.randomUUID());
        when(assetRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.get(tenantId, userId, "asset_user", assetId));
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.ASSET_NOT_FOUND);
        verify(assetAssembler, never()).toResponse(any(), any(), any());
    }
}
