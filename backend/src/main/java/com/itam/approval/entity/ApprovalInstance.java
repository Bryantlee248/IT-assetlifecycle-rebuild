package com.itam.approval.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * 审批实例（租户级）：一次生命周期动作审批的聚合根。
 * 记录业务上下文（asset_id / transition_id / action_code / from_state / to_state / 申请人 / 原因），
 * 供审批详情页与末节点回调使用。status / current_node_order 驱动流转进度。
 */
@Entity
@Table(name = "approval_instances")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalInstance extends TenantEntity {

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "asset_id", nullable = false, columnDefinition = "uuid")
    private UUID assetId;

    @Column(name = "transition_id", columnDefinition = "uuid")
    private UUID transitionId;

    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    @Column(name = "action_name", length = 128)
    private String actionName;

    @Column(name = "from_state", nullable = false, length = 64)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 64)
    private String toState;

    @Column(name = "applicant_id", nullable = false, columnDefinition = "uuid")
    private UUID applicantId;

    @Column(name = "applicant_name", length = 128)
    private String applicantName;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstanceStatus status = InstanceStatus.PENDING;

    @Column(name = "current_node_order", nullable = false)
    private int currentNodeOrder = 1;

    @Column(name = "title", length = 256)
    private String title;
}
