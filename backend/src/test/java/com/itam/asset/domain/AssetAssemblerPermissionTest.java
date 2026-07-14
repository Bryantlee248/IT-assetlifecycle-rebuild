package com.itam.asset.domain;

import com.itam.asset.dto.AssetResponse;
import com.itam.asset.entity.Asset;
import com.itam.metadata.domain.FieldPermissionService;
import com.itam.metadata.domain.FieldPermissionView;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P0-2 响应层字段权限装配单元测试：敏感字段脱敏、隐藏、顶层固定字段按权限过滤。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetAssemblerPermissionTest {

    @Mock FieldDefinitionRepository fieldDefinitionRepository;
    @Mock FieldPermissionService fieldPermissionService;
    @Mock AssetTypeRepository assetTypeRepository;
    @Mock FieldCryptoService fieldCryptoService;

    private AssetAssembler assembler;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID assetTypeId = UUID.randomUUID();

    private final FieldPermissionView VISIBLE = new FieldPermissionView(true, true, false, true, null);
    private final FieldPermissionView MASKED = new FieldPermissionView(true, false, true, false, "last4");
    private final FieldPermissionView HIDDEN = new FieldPermissionView(false, false, true, false, null);

    @BeforeEach
    void setUp() {
        assembler = new AssetAssembler(fieldDefinitionRepository, fieldPermissionService, assetTypeRepository, fieldCryptoService);
    }

    private FieldDefinition licenseKeyDef() {
        FieldDefinition fd = new FieldDefinition();
        fd.setFieldCode("license_key");
        fd.setStorageType("encrypted");
        fd.setSensitive(true);
        fd.setMaskRule("last4");
        return fd;
    }

    private Asset assetWithLicense() {
        Asset asset = new Asset();
        asset.setTenantId(tenantId);
        asset.setAssetTypeId(assetTypeId);
        asset.setAssetNo("A-1");
        asset.setAssetName("服务器");
        asset.setLifecycleStatus("planned");
        asset.setStatus("active");
        asset.setAttributes(Map.of("license_key", "enc:abc"));
        return asset;
    }

    private void stubCommon(AssetType at) {
        when(assetTypeRepository.findByTenantIdAndId(tenantId, assetTypeId)).thenReturn(Optional.of(at));
        when(fieldDefinitionRepository.findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, assetTypeId))
                .thenReturn(List.of(licenseKeyDef()));
        when(fieldCryptoService.decrypt("enc:abc")).thenReturn("LIC-12345");
        when(fieldPermissionService.resolve(any(), any(), any(), any())).thenReturn(VISIBLE);
        when(fieldPermissionService.resolveByCode(any(), any(), any(), any())).thenReturn(VISIBLE);
    }

    @Test
    void auditor_sees_license_key_masked() {
        AssetType at = new AssetType();
        at.setTypeName("服务器");
        stubCommon(at);
        when(fieldPermissionService.resolveByCode(any(), eq("auditor"), any(), eq("license_key"))).thenReturn(MASKED);

        AssetResponse r = assembler.toResponse(assetWithLicense(), tenantId, "auditor");
        assertThat(r.fields()).containsKey("license_key");
        assertThat((String) r.fields().get("license_key")).startsWith("***");
    }

    @Test
    void asset_user_license_key_hidden() {
        AssetType at = new AssetType();
        at.setTypeName("服务器");
        stubCommon(at);
        when(fieldPermissionService.resolveByCode(any(), eq("asset_user"), any(), eq("license_key"))).thenReturn(HIDDEN);

        AssetResponse r = assembler.toResponse(assetWithLicense(), tenantId, "asset_user");
        assertThat(r.fields()).doesNotContainKey("license_key");
    }

    @Test
    void invisible_top_field_returns_null() {
        AssetType at = new AssetType();
        at.setTypeName("服务器");
        stubCommon(at);
        when(fieldPermissionService.resolve(any(), any(), any(), eq("asset_name"))).thenReturn(HIDDEN);

        AssetResponse r = assembler.toResponse(assetWithLicense(), tenantId, "asset_admin");
        assertThat(r.assetName()).isNull();
    }
}
