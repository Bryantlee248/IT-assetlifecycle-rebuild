package com.itam.asset.entity;

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
 * 资产关系（租户级）。不写死在某类型，relation_type 受枚举约束。
 */
@Entity
@Table(name = "asset_relations")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class AssetRelation extends TenantEntity {

    @Column(name = "source_asset_id", nullable = false, columnDefinition = "uuid")
    private UUID sourceAssetId;

    @Column(name = "target_asset_id", nullable = false, columnDefinition = "uuid")
    private UUID targetAssetId;

    @Column(name = "relation_type", nullable = false)
    private String relationType; // installed_on / binds_to / depends_on / located_in / uses

    @Column(columnDefinition = "text")
    private String description;
}
