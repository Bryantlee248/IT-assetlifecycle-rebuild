package com.itam.lifecycle.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生命周期事件（租户级，只追加）。
 * 每次成功流转写一条；approval_required 分支不写事件。
 * form_data / attachment_ids 为 JSONB（MVP-2 仅落库留痕，不回写资产属性）。
 */
@Entity
@Table(name = "lifecycle_events")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleEvent extends TenantEntity {

    @Column(name = "asset_id", nullable = false, columnDefinition = "uuid")
    private UUID assetId;

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "transition_id", columnDefinition = "uuid")
    private UUID transitionId;

    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 128)
    private String actionName;

    @Column(name = "from_state", nullable = false, length = 64)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 64)
    private String toState;

    @Column(name = "operator_id", nullable = false, columnDefinition = "uuid")
    private UUID operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> formData = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> attachmentIds = new ArrayList<>();

    @Column(name = "approval_instance_id", columnDefinition = "uuid")
    private UUID approvalInstanceId;
}
