package com.itam.approval.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审批任务（租户级）。approver_id 为具体平台用户，是"非审批人不能审批"的硬保证：
 * approve/reject 仅作用于 approver_id == 当前用户 的 pending 任务。
 * 同一节点可因 ROLE 扇出产生多个 pending 任务，首批生效后其余置 cancelled。
 */
@Entity
@Table(name = "approval_tasks")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalTask extends TenantEntity {

    @Column(name = "instance_id", nullable = false, columnDefinition = "uuid")
    private UUID instanceId;

    @Column(name = "node_order", nullable = false)
    private int nodeOrder = 1;

    @Column(name = "approver_id", nullable = false, columnDefinition = "uuid")
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 16)
    private ApproverType approverType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "decided_by", columnDefinition = "uuid")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
