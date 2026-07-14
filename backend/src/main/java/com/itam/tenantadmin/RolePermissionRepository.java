package com.itam.tenantadmin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    void deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    boolean existsByTenantIdAndRoleIdAndPermissionCode(UUID tenantId, UUID roleId, String permissionCode);
}
