package com.itam.lifecycle.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * 生命周期模板（租户级）。
 * 优先按 asset_type_id -> asset_types.lifecycle_template_id 命中，
 * 兜底按 asset_kind 匹配该租户默认模板。
 */
@Entity
@Table(name = "lifecycle_templates")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleTemplate extends TenantEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "asset_kind", length = 32)
    private String assetKind;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
