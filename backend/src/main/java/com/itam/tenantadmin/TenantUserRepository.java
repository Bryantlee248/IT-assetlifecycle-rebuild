package com.itam.tenantadmin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByTenantIdAndPlatformUserId(UUID tenantId, UUID platformUserId);

    Optional<TenantUser> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TenantUser> findByTenantId(UUID tenantId);

    List<TenantUser> findByPlatformUserId(UUID platformUserId);

    Page<TenantUser> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndPlatformUserId(UUID tenantId, UUID platformUserId);
}
