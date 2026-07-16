package com.itam.approval.repository;

import com.itam.approval.entity.ApprovalNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批节点仓储。查询强制 tenant_id 隔离，并按 node_order 排序以支持顺序多节点。
 */
@Repository
public interface ApprovalNodeRepository extends JpaRepository<ApprovalNode, UUID> {

    List<ApprovalNode> findByTenantIdAndTemplateIdAndDeletedFalseOrderByNodeOrderAsc(
            UUID tenantId, UUID templateId);

    Optional<ApprovalNode> findByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(
            UUID tenantId, UUID templateId, int nodeOrder);

    /** 是否存在下一节点（判断当前节点是否为末节点）。 */
    boolean existsByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(
            UUID tenantId, UUID templateId, int nodeOrder);
}
