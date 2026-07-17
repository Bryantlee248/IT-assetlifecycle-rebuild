package com.itam.approval.repository;

import com.itam.approval.entity.ApprovalInstance;
import com.itam.approval.entity.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批实例仓储。查询强制 tenant_id 隔离。实例列表按创建时间倒序。
 */
@Repository
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {

    Optional<ApprovalInstance> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    List<ApprovalInstance> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId);

    List<ApprovalInstance> findByTenantIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, InstanceStatus status);

    /** 批量加载待办公示所需的实例上下文（强制 tenant_id 隔离）。 */
    List<ApprovalInstance> findByTenantIdAndIdInAndDeletedFalse(UUID tenantId, List<UUID> ids);
}
