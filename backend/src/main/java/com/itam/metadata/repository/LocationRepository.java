package com.itam.metadata.repository;

import com.itam.metadata.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 位置仓储。树形结构通过 parent_id 自引用；只读端点 /metadata/locations/tree 使用。
 */
@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    List<Location> findByTenantIdAndParentIdOrderBySortOrderAsc(UUID tenantId, UUID parentId);

    Optional<Location> findByTenantIdAndId(UUID tenantId, UUID id);
}
