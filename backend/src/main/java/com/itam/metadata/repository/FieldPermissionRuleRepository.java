package com.itam.metadata.repository;

import com.itam.metadata.entity.FieldPermissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 字段权限规则仓储（预留）。
 * MVP-1 默认规则由内存矩阵实现（FieldPermissionService），表内通常不喂数据；
 * 此 Repository 仅作为未来 MVP-3 配置页的落库能力。
 */
@Repository
public interface FieldPermissionRuleRepository extends JpaRepository<FieldPermissionRule, UUID> {

    List<FieldPermissionRule> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    Optional<FieldPermissionRule> findByTenantIdAndRoleIdAndAssetTypeIdAndFieldCode(
            UUID tenantId, UUID roleId, UUID assetTypeId, String fieldCode);
}
