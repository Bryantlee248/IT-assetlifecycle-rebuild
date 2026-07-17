package com.itam.approval.repository;

import com.itam.approval.entity.ApprovalTask;
import com.itam.approval.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批任务仓储。查询强制 tenant_id 隔离。
 * 我的待办：按 approver_id + status=pending 过滤；
 * 实例任务历史：按 instance_id 列出；当前节点任务：按 instance_id + node_order 过滤。
 */
@Repository
public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {

    /** 我的待办：当前用户为 pending 审批人的任务（按创建时间倒序）。 */
    List<ApprovalTask> findByTenantIdAndApproverIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID approverId, TaskStatus status);

    /** 我的全部任务（任意状态，按创建时间倒序），用于"已办/全部"筛选。 */
    List<ApprovalTask> findByTenantIdAndApproverIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID approverId);

    /** 实例全部任务历史（按节点顺序、创建时间）。 */
    List<ApprovalTask> findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(
            UUID tenantId, UUID instanceId);

    /** 当前节点全部任务（用于"首批生效，其余取消"）。 */
    List<ApprovalTask> findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(
            UUID tenantId, UUID instanceId, int nodeOrder);

    /** 当前节点中"指定审批人"的那条任务（用于拦截非审批人）。 */
    Optional<ApprovalTask> findByTenantIdAndInstanceIdAndNodeOrderAndApproverIdAndDeletedFalse(
            UUID tenantId, UUID instanceId, int nodeOrder, UUID approverId);
}
