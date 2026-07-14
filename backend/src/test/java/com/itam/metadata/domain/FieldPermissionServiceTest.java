package com.itam.metadata.domain;

import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.repository.FieldDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 字段权限默认引擎单元测试：roleCode 驱动基线矩阵，叠加字段可见/可编辑/敏感属性。
 */
@ExtendWith(MockitoExtension.class)
class FieldPermissionServiceTest {

    @Mock
    FieldDefinitionRepository fieldDefinitionRepository;

    private FieldPermissionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID assetTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FieldPermissionService(fieldDefinitionRepository);
    }

    private FieldDefinition def(String code, boolean visible, boolean editable, boolean sensitive, String maskRule) {
        FieldDefinition f = new FieldDefinition();
        f.setFieldCode(code);
        f.setVisible(visible);
        f.setEditable(editable);
        f.setSensitive(sensitive);
        f.setMaskRule(maskRule);
        return f;
    }

    @Test
    void tenant_admin_sees_and_edits_normal_field() {
        FieldDefinition f = def("cpu_cores", true, true, false, null);
        FieldPermissionView v = service.resolveByCode(tenantId, "tenant_admin", f, "cpu_cores");
        assertThat(v.visible()).isTrue();
        assertThat(v.editable()).isTrue();
        assertThat(v.masked()).isFalse();
    }

    @Test
    void auditor_cannot_edit_normal_field() {
        FieldDefinition f = def("cpu_cores", true, true, false, null);
        FieldPermissionView v = service.resolveByCode(tenantId, "auditor", f, "cpu_cores");
        assertThat(v.visible()).isTrue();
        assertThat(v.editable()).isFalse();
    }

    @Test
    void auditor_sees_sensitive_field_masked() {
        FieldDefinition f = def("software_license.license_key", true, true, true, "last4");
        FieldPermissionView v = service.resolveByCode(tenantId, "auditor", f, "software_license.license_key");
        assertThat(v.visible()).isTrue();
        assertThat(v.masked()).isTrue();
        assertThat(v.maskRule()).isEqualTo("last4");
    }

    @Test
    void system_field_not_editable_for_any_role() {
        FieldDefinition f = def("lifecycle_status", true, true, false, null);
        FieldPermissionView v = service.resolveByCode(tenantId, "tenant_admin", f, "lifecycle_status");
        assertThat(v.editable()).isFalse();
    }

    @Test
    void hidden_field_not_visible() {
        FieldDefinition f = def("secret", false, true, false, null);
        FieldPermissionView v = service.resolveByCode(tenantId, "tenant_admin", f, "secret");
        assertThat(v.visible()).isFalse();
    }

    @Test
    void resolve_unknown_field_defaults_visible_editable_for_admin() {
        when(fieldDefinitionRepository.findByTenantIdAndAssetTypeIdAndFieldCode(tenantId, assetTypeId, "ghost"))
                .thenReturn(Optional.empty());
        FieldPermissionView v = service.resolve(tenantId, "asset_admin", assetTypeId, "ghost");
        assertThat(v.visible()).isTrue();
        assertThat(v.editable()).isTrue();
    }

    @Test
    void resolve_uses_definition_when_present() {
        FieldDefinition f = def("cpu_cores", true, false, false, null);
        when(fieldDefinitionRepository.findByTenantIdAndAssetTypeIdAndFieldCode(tenantId, assetTypeId, "cpu_cores"))
                .thenReturn(Optional.of(f));
        FieldPermissionView v = service.resolve(tenantId, "asset_admin", assetTypeId, "cpu_cores");
        assertThat(v.editable()).isFalse(); // 定义中 editable=false 优先
    }

    // ===== P0-2 五角色矩阵（PRD §5）=====

    @Test
    void asset_user_hides_sensitive_field() {
        FieldDefinition f = def("software_license.license_key", true, true, true, "last4");
        FieldPermissionView v = service.resolveByCode(tenantId, "asset_user", f, "software_license.license_key");
        assertThat(v.visible()).isFalse();
        assertThat(v.exportable()).isFalse();
    }

    @Test
    void asset_user_sees_normal_field_but_readonly() {
        FieldDefinition f = def("cpu_cores", true, true, false, null);
        FieldPermissionView v = service.resolveByCode(tenantId, "asset_user", f, "cpu_cores");
        assertThat(v.visible()).isTrue();
        assertThat(v.editable()).isFalse();
    }

    @Test
    void asset_operator_masks_sensitive_but_not_edit() {
        FieldDefinition f = def("software_license.license_key", true, true, true, "last4");
        FieldPermissionView v = service.resolveByCode(tenantId, "asset_operator", f, "software_license.license_key");
        assertThat(v.visible()).isTrue();
        assertThat(v.masked()).isTrue();
        assertThat(v.editable()).isFalse();
        assertThat(v.exportable()).isFalse();
    }

    @Test
    void tenant_admin_edits_sensitive_without_mask() {
        FieldDefinition f = def("software_license.license_key", true, true, true, "last4");
        FieldPermissionView v = service.resolveByCode(tenantId, "tenant_admin", f, "software_license.license_key");
        assertThat(v.visible()).isTrue();
        assertThat(v.editable()).isTrue();
        assertThat(v.masked()).isFalse();
        assertThat(v.exportable()).isTrue();
    }

    @Test
    void auditor_readonly_sensitive_masked() {
        FieldDefinition f = def("software_license.license_key", true, true, true, "last4");
        FieldPermissionView v = service.resolveByCode(tenantId, "auditor", f, "software_license.license_key");
        assertThat(v.visible()).isTrue();
        assertThat(v.masked()).isTrue();
        assertThat(v.editable()).isFalse();
    }
}
