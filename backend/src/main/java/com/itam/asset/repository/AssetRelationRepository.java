package com.itam.asset.repository;

import com.itam.asset.entity.AssetRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产关系仓储。按源资产查询；去重检查 (tenant, source, target, type)。
 */
@Repository
public interface AssetRelationRepository extends JpaRepository<AssetRelation, UUID> {

    List<AssetRelation> findByTenantIdAndSourceAssetId(UUID tenantId, UUID sourceAssetId);

    Optional<AssetRelation> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndSourceAssetIdAndTargetAssetIdAndRelationType(
            UUID tenantId, UUID sourceAssetId, UUID targetAssetId, String relationType);
}
