package com.itam.lifecycle.repository;

import com.itam.lifecycle.entity.LifecycleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 生命周期状态仓储。所有查询强制 tenant_id 隔离，并显式排除软删行。
 */
@Repository
public interface LifecycleStateRepository extends JpaRepository<LifecycleState, UUID> {

    List<LifecycleState> findByTenantIdAndTemplateIdAndDeletedFalse(UUID tenantId, UUID templateId);

    Optional<LifecycleState> findByTenantIdAndTemplateIdAndStateCodeAndDeletedFalse(
            UUID tenantId, UUID templateId, String stateCode);
}
