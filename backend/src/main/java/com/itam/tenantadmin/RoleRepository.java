package com.itam.tenantadmin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByTenantId(UUID tenantId);

    Page<Role> findByTenantId(Pageable pageable);

    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
