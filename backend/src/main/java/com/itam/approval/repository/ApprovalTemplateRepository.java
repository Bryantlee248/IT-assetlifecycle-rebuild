package com.itam.approval.repository;

import com.itam.approval.entity.ApprovalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 审批模板仓储。查询强制 tenant_id 隔离。
 */
@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, UUID> {

    Optional<ApprovalTemplate> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
}
