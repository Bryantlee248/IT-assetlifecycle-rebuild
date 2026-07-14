package com.itam.metadata.repository;

import com.itam.metadata.entity.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 字段定义仓储。所有查询强制 tenant_id 隔离，并通过 @Where 软删过滤。
 */
@Repository
public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {

    List<FieldDefinition> findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(UUID tenantId, UUID assetTypeId);

    Optional<FieldDefinition> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<FieldDefinition> findByTenantIdAndAssetTypeIdAndFieldCode(
            UUID tenantId, UUID assetTypeId, String fieldCode);

    boolean existsByTenantIdAndAssetTypeIdAndFieldCode(UUID tenantId, UUID assetTypeId, String fieldCode);
}
