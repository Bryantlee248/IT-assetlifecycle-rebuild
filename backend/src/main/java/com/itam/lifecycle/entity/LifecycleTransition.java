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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 生命周期流转/动作定义（租户级）。
 * guard_rule 为 JSONB，结构：{ requireFields:[...], requireAttributeFields:[...], requireAttachment:bool }
 */
@Entity
@Table(name = "lifecycle_transitions")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleTransition extends TenantEntity {

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 128)
    private String actionName;

    @Column(name = "from_state", nullable = false, length = 64)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 64)
    private String toState;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = false;

    @Column(name = "approval_template_id", columnDefinition = "uuid")
    private UUID approvalTemplateId;

    @Column(name = "require_attachment", nullable = false)
    private boolean requireAttachment = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "guard_rule", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> guardRule = new LinkedHashMap<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
