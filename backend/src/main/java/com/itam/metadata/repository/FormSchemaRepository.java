package com.itam.metadata.repository;

import com.itam.metadata.entity.FormSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 表单配置仓储。每个资产类型至多一份启用配置。
 */
@Repository
public interface FormSchemaRepository extends JpaRepository<FormSchema, UUID> {

    Optional<FormSchema> findByTenantIdAndAssetTypeId(UUID tenantId, UUID assetTypeId);
}
