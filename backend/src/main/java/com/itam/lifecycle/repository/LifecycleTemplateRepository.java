package com.itam.lifecycle.repository;

import com.itam.lifecycle.entity.LifecycleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 生命周期模板仓储。所有查询强制 tenant_id 隔离。
 *
 * <p>派生查询方法名已显式带上 {@code AndDeletedFalse} 与 {@code OrderBy}，
 * 与实体层 {@code @Where(clause = "deleted = false")} 双保险，复查者可直接看出过滤契约。
 */
@Repository
public interface LifecycleTemplateRepository extends JpaRepository<LifecycleTemplate, UUID> {

    Optional<LifecycleTemplate> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    List<LifecycleTemplate> findByTenantIdAndAssetKindAndDeletedFalse(UUID tenantId, String assetKind);

    /** 兜底默认模板：按 asset_kind 匹配启用(enabled=true)且未软删的模板，按 created_at ASC 稳定排序。 */
    List<LifecycleTemplate> findByTenantIdAndAssetKindAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(
            UUID tenantId, String assetKind);

    List<LifecycleTemplate> findAllByTenantIdAndEnabledTrueAndDeletedFalseOrderByCreatedAtAsc(UUID tenantId);
}
