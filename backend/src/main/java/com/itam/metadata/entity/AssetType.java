package com.itam.metadata.entity;

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
 * 资产类型（租户级），树形结构 parent_id 自引用。
 */
@Entity
@Table(name = "asset_types")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class AssetType extends TenantEntity {

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "asset_kind", nullable = false)
    private String assetKind; // tangible / intangible

    @Column(name = "lifecycle_template_id", columnDefinition = "uuid")
    private UUID lifecycleTemplateId;

    private String icon;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
