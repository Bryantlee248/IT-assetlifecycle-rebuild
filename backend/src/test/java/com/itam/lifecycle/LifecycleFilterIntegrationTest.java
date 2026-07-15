package com.itam.lifecycle;

import com.itam.lifecycle.entity.LifecycleEvent;
import com.itam.lifecycle.entity.LifecycleState;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.LifecycleEventRepository;
import com.itam.lifecycle.repository.LifecycleStateRepository;
import com.itam.lifecycle.repository.LifecycleTemplateRepository;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生命周期查询边界集成测试（Testcontainers + 真实 PostgreSQL + Flyway 迁移）。
 *
 * <p>默认跳过：仅当环境变量 {@code ITAM_INTEGRATION=true} 且本机有 Docker 时执行，
 * 以免无容器环境导致本地 {@code mvn test} 失败。用于证明 SQL 层 {@code deleted = false} 过滤生效——
 * 各仓储方法名已显式带 {@code AndDeletedFalse}/{@code OrderBy}（与实体 {@code @Where} 双保险）。
 *
 * <p>所有用例使用随机 tenant_id 与唯一 asset_kind，避免与 V7 种子数据冲突。
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "ITAM_INTEGRATION", matches = "true")
class LifecycleFilterIntegrationTest {

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
    private LifecycleTemplateRepository lifecycleTemplateRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private LifecycleStateRepository lifecycleStateRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private LifecycleTransitionRepository lifecycleTransitionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private LifecycleEventRepository lifecycleEventRepository;

    private UUID newTenant() {
        return UUID.randomUUID();
    }

    private LifecycleTemplate aliveTemplate(UUID tenantId, String assetKind, String name) {
        LifecycleTemplate t = new LifecycleTemplate();
        t.setTenantId(tenantId);
        t.setAssetKind(assetKind);
        t.setName(name);
        t.setEnabled(true);
        t.setDeleted(false);
        return t;
    }

    @Test
    void templateFallback_excludesSoftDeleted() {
        UUID tenantId = newTenant();
        String assetKind = "TESTKIND_" + UUID.randomUUID().toString().replace("-", "");

        LifecycleTemplate alive = aliveTemplate(tenantId, assetKind, "alive-" + UUID.randomUUID());
        LifecycleTemplate deleted = aliveTemplate(tenantId, assetKind, "deleted-" + UUID.randomUUID());
        deleted.setDeleted(true); // enabled=true 但已软删，专门验证 DeletedFalse 过滤

        lifecycleTemplateRepository.saveAndFlush(alive);
        lifecycleTemplateRepository.saveAndFlush(deleted);

        List<LifecycleTemplate> result = lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, assetKind);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(alive.getId());
        assertThat(result.get(0).isDeleted()).isFalse();
    }

    @Test
    void templateFallback_ordersByCreatedAtAsc() throws Exception {
        UUID tenantId = newTenant();
        String assetKind = "TESTKIND_" + UUID.randomUUID().toString().replace("-", "");

        LifecycleTemplate earlier = aliveTemplate(tenantId, assetKind, "earlier-" + UUID.randomUUID());
        lifecycleTemplateRepository.saveAndFlush(earlier);
        // @CreationTimestamp 在 flush 时写入，sleep 保证两条记录 created_at 不同
        Thread.sleep(60);
        LifecycleTemplate later = aliveTemplate(tenantId, assetKind, "later-" + UUID.randomUUID());
        lifecycleTemplateRepository.saveAndFlush(later);

        List<LifecycleTemplate> result = lifecycleTemplateRepository
                .findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(tenantId, assetKind);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(earlier.getId());
        assertThat(result.get(1).getId()).isEqualTo(later.getId());
    }

    @Test
    void state_excludesSoftDeleted() {
        UUID tenantId = newTenant();
        UUID templateId = UUID.randomUUID();

        LifecycleState alive = new LifecycleState();
        alive.setTenantId(tenantId);
        alive.setTemplateId(templateId);
        alive.setStateCode("planned");
        alive.setStateName("规划/申请");
        alive.setDeleted(false);

        LifecycleState deleted = new LifecycleState();
        deleted.setTenantId(tenantId);
        deleted.setTemplateId(templateId);
        deleted.setStateCode("retired");
        deleted.setStateName("已退役");
        deleted.setDeleted(true);

        lifecycleStateRepository.saveAndFlush(alive);
        lifecycleStateRepository.saveAndFlush(deleted);

        List<LifecycleState> all = lifecycleStateRepository
                .findByTenantIdAndTemplateIdAndDeletedFalse(tenantId, templateId);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(alive.getId());

        Optional<LifecycleState> byCode = lifecycleStateRepository
                .findByTenantIdAndTemplateIdAndStateCodeAndDeletedFalse(tenantId, templateId, "retired");
        assertThat(byCode).isEmpty();
    }

    @Test
    void transition_excludesSoftDeletedAndOrdersBySortOrderAsc() {
        UUID tenantId = newTenant();
        UUID templateId = UUID.randomUUID();
        String fromState = "planned";

        LifecycleTransition t1 = new LifecycleTransition();
        t1.setTenantId(tenantId);
        t1.setTemplateId(templateId);
        t1.setActionCode("deploy");
        t1.setActionName("部署");
        t1.setFromState(fromState);
        t1.setToState("in_use");
        t1.setSortOrder(1);
        t1.setDeleted(false);

        LifecycleTransition t2 = new LifecycleTransition();
        t2.setTenantId(tenantId);
        t2.setTemplateId(templateId);
        t2.setActionCode("submit");
        t2.setActionName("提交");
        t2.setFromState(fromState);
        t2.setToState("purchasing");
        t2.setSortOrder(2);
        t2.setDeleted(false);

        LifecycleTransition deleted = new LifecycleTransition();
        deleted.setTenantId(tenantId);
        deleted.setTemplateId(templateId);
        deleted.setActionCode("ghost");
        deleted.setActionName("幽灵");
        deleted.setFromState(fromState);
        deleted.setToState("disposed");
        deleted.setSortOrder(3);
        deleted.setDeleted(true);

        lifecycleTransitionRepository.saveAndFlush(t1);
        lifecycleTransitionRepository.saveAndFlush(t2);
        lifecycleTransitionRepository.saveAndFlush(deleted);

        List<LifecycleTransition> result = lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, fromState);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(t1.getId());
        assertThat(result.get(1).getId()).isEqualTo(t2.getId());
        assertThat(result.stream().map(LifecycleTransition::getActionCode).toList())
                .containsExactly("deploy", "submit")
                .doesNotContain("ghost");
    }

    @Test
    void event_excludesSoftDeleted() {
        UUID tenantId = newTenant();
        UUID assetId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        LifecycleEvent alive = new LifecycleEvent();
        alive.setTenantId(tenantId);
        alive.setAssetId(assetId);
        alive.setTemplateId(templateId);
        alive.setActionCode("deploy");
        alive.setActionName("部署");
        alive.setFromState("planned");
        alive.setToState("in_use");
        alive.setOperatorId(operatorId);
        alive.setDeleted(false);

        LifecycleEvent deleted = new LifecycleEvent();
        deleted.setTenantId(tenantId);
        deleted.setAssetId(assetId);
        deleted.setTemplateId(templateId);
        deleted.setActionCode("dispose");
        deleted.setActionName("处置");
        deleted.setFromState("retired");
        deleted.setToState("disposed");
        deleted.setOperatorId(operatorId);
        deleted.setDeleted(true);

        lifecycleEventRepository.saveAndFlush(alive);
        lifecycleEventRepository.saveAndFlush(deleted);

        List<LifecycleEvent> result = lifecycleEventRepository
                .findByTenantIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, assetId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(alive.getId());
    }
}
