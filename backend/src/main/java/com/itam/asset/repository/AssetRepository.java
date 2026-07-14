package com.itam.asset.repository;

import com.itam.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 资产仓储。所有查询强制 tenant_id 隔离（Repository 方法显式传入 tenantId）。
 * 列表查询通过 JpaSpecificationExecutor + AssetSpecifications 实现多条件动态过滤。
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>,
        JpaSpecificationExecutor<Asset> {

    Optional<Asset> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndAssetNo(UUID tenantId, String assetNo);

    boolean existsByTenantIdAndSerialNo(UUID tenantId, String serialNo);
}
