package com.itam.metadata.repository;

import com.itam.metadata.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产类型仓储。所有查询强制 tenant_id 隔离。
 * 树形结构通过 parent_id 自引用，列表按 sort_order 升序。
 */
@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, UUID> {

    List<AssetType> findByTenantIdOrderBySortAsc(UUID tenantId);

    List<AssetType> findByTenantIdAndParentIdOrderBySortAsc(UUID tenantId, UUID parentId);

    Optional<AssetType> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndTypeCode(UUID tenantId, String typeCode);
}
