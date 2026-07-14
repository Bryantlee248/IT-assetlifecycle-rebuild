package com.itam.asset;

import com.itam.asset.domain.AssetFieldMappingService;
import com.itam.asset.domain.AssetFieldMappingService.SplitResult;
import com.itam.asset.entity.Asset;
import com.itam.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资产数据层集成测试（Testcontainers + 真实 PostgreSQL + Flyway 迁移）。
 *
 * 默认跳过：仅当环境变量 ITAM_INTEGRATION=true 且本机有 Docker 时执行，
 * 以免无容器环境导致 CI/本地 `mvn test` 失败。用于验证：
 *  ① 热点物理列与 attributes(JSONB) 的拆分落库与回读
 *  ② 租户隔离（跨租户按 id 查询不可见）
 */
@DataJpaTest
@Testcontainers
@Import(AssetFieldMappingService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "ITAM_INTEGRATION", matches = "true")
class AssetIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.driver-class-name", PG::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AssetRepository assetRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private AssetFieldMappingService mappingService;

    private Asset baseAsset(UUID tenantId) {
        Asset a = new Asset();
        a.setTenantId(tenantId);
        a.setAssetTypeId(UUID.randomUUID());
        a.setAssetKind("DEVICE");
        a.setAssetName("Server-1");
        a.setAssetNo("A-001");
        a.setStatus("active");
        a.setSourceType("manual");
        a.setLifecycleStatus("planned");
        return a;
    }

    @Test
    void hotspot_and_attributes_persist_separately() {
        Asset a = baseAsset(UUID.randomUUID());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serial_no", "SN-X");
        values.put("brand", "Dell");
        values.put("cpu_cores", 16);
        SplitResult split = mappingService.split(values);
        split.physical().forEach(a::setPhysicalValue);
        a.setAttributes(split.attributes());

        Asset saved = assetRepository.save(a);
        Asset reloaded = assetRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPhysicalValue("serial_no")).isEqualTo("SN-X");
        assertThat(reloaded.getPhysicalValue("brand")).isEqualTo("Dell");
        assertThat(reloaded.getAttributes()).containsEntry("cpu_cores", 16);
        assertThat(reloaded.getAttributes()).doesNotContainKey("serial_no");
    }

    @Test
    void tenant_isolation_by_id() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Asset a = baseAsset(tenantA);
        assetRepository.save(a);

        Optional<Asset> foundByOther = assetRepository.findByTenantIdAndId(tenantB, a.getId());
        assertThat(foundByOther).isEmpty();

        Optional<Asset> foundByOwner = assetRepository.findByTenantIdAndId(tenantA, a.getId());
        assertThat(foundByOwner).isPresent();
    }
}
