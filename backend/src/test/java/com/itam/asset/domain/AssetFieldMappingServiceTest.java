package com.itam.asset.domain;

import com.itam.asset.domain.AssetFieldMappingService.SplitResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资产字段映射（物理列 ↔ attributes JSONB）单元测试。
 */
class AssetFieldMappingServiceTest {

    private final AssetFieldMappingService service = new AssetFieldMappingService();

    @Test
    void split_routes_hotspot_to_physical_and_rest_to_attributes() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serial_no", "SN123");                       // 热点
        values.put("brand", "Dell");                            // 热点
        values.put("cpu_cores", 8);                             // 扩展
        values.put("software_license.license_key", "ABC");      // 扩展

        SplitResult r = service.split(values);
        assertThat(r.physical())
                .containsEntry("serial_no", "SN123")
                .containsEntry("brand", "Dell");
        assertThat(r.physical()).doesNotContainKey("cpu_cores");
        assertThat(r.attributes())
                .containsEntry("cpu_cores", 8)
                .containsEntry("software_license.license_key", "ABC");
        assertThat(r.attributes()).doesNotContainKey("serial_no");
    }

    @Test
    void split_ignores_null_values() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("brand", null);
        values.put("cpu_cores", 4);
        SplitResult r = service.split(values);
        assertThat(r.physical()).doesNotContainKey("brand");
        assertThat(r.attributes()).containsEntry("cpu_cores", 4);
    }
}
