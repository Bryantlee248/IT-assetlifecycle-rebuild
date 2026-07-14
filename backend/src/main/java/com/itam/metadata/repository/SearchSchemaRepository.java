package com.itam.metadata.repository;

import com.itam.metadata.entity.SearchSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 查询配置仓储。每个资产类型至多一份启用配置。
 */
@Repository
public interface SearchSchemaRepository extends JpaRepository<SearchSchema, UUID> {

    Optional<SearchSchema> findByTenantIdAndAssetTypeId(UUID tenantId, UUID assetTypeId);
}
