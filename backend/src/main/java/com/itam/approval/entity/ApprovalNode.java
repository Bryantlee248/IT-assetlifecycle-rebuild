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

import java.util.UUID;

/**
 * 审批节点（租户级）。单节点默认 1 行 node_order=1。
 * USER -> approver_user_id 落地具体用户；ROLE -> approver_role_id 提交时扇出为多名成员各 1 任务。
 */
@Entity
@Table(name = "approval_nodes")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalNode extends TenantEntity {

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "node_order", nullable = false)
    private int nodeOrder = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 16)
    private ApproverType approverType;

    @Column(name = "approver_user_id", columnDefinition = "uuid")
    private UUID approverUserId;

    @Column(name = "approver_role_id", columnDefinition = "uuid")
    private UUID approverRoleId;
}
