package com.itam.metadata.repository;

import com.itam.metadata.entity.StatePermissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 状态权限规则仓储。查询强制 tenant_id 隔离。
 * 规模较小，按租户全量加载后在 StatePermissionService 内存中按
 * role × assetType × state 过滤并聚合 allowed_actions 并集。
 */
@Repository
public interface StatePermissionRuleRepository extends JpaRepository<StatePermissionRule, UUID> {

    List<StatePermissionRule> findByTenantIdAndDeletedFalse(UUID tenantId);
}
