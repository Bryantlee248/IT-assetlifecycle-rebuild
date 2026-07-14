package com.itam.metadata.repository;

import com.itam.metadata.entity.ListViewSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 列表配置仓储。每个资产类型至多一份启用配置。
 */
@Repository
public interface ListViewSchemaRepository extends JpaRepository<ListViewSchema, UUID> {

    Optional<ListViewSchema> findByTenantIdAndAssetTypeId(UUID tenantId, UUID assetTypeId);
}
