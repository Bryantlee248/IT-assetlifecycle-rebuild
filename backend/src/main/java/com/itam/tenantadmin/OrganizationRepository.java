package com.itam.tenantadmin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByTenantIdOrderBySortAsc(UUID tenantId);

    List<Organization> findByTenantIdAndParentIdOrderBySortAsc(UUID tenantId, UUID parentId);

    Optional<Organization> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
