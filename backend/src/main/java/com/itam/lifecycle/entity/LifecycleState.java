package com.itam.lifecycle.entity;

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
 * 生命周期状态定义（租户级）。每个模板下 state_code 唯一。
 * 各模板仅一个 is_initial=true（即 planned）。
 */
@Entity
@Table(name = "lifecycle_states")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleState extends TenantEntity {

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "state_code", nullable = false, length = 64)
    private String stateCode;

    @Column(name = "state_name", nullable = false, length = 128)
    private String stateName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_initial", nullable = false)
    private boolean initial = false;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
