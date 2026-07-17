package com.itam.approval.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * 审批模板（租户级）。单节点默认；asset_kind / action_code 为便于检索的冗余字段，
 * 实际审批关联由 lifecycle_transitions.approval_template_id 驱动。
 * asset_kind 为空表示全大类适用。
 */
@Entity
@Table(name = "approval_templates")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalTemplate extends TenantEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "asset_kind", length = 32)
    private String assetKind;

    @Column(name = "action_code", length = 64)
    private String actionCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
