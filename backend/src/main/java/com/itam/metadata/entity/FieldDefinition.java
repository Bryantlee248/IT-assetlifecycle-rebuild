package com.itam.metadata.entity;

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

import java.util.Map;
import java.util.UUID;

/**
 * 字段定义（租户级，归属资产类型）。
 * storage_type ∈ {physical, jsonb, encrypted}；unique_scope ∈ {none, tenant, asset_type}。
 * physical 字段的 physical_column 必须落在热点白名单（HotspotColumn）。
 */
@Entity
@Table(name = "field_definitions")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class FieldDefinition extends TenantEntity {

    @Column(name = "asset_type_id", nullable = false, columnDefinition = "uuid")
    private UUID assetTypeId;

    @Column(name = "field_code", nullable = false)
    private String fieldCode;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "field_type", nullable = false)
    private String fieldType; // 16 值枚举

    @Column(name = "storage_type", nullable = false)
    private String storageType; // physical / jsonb / encrypted

    @Column(name = "physical_column")
    private String physicalColumn;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "unique_scope", nullable = false)
    private String uniqueScope = "none"; // none / tenant / asset_type

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value", columnDefinition = "jsonb")
    private Map<String, Object> defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rule", columnDefinition = "jsonb")
    private Map<String, Object> validationRule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_source", columnDefinition = "jsonb")
    private Map<String, Object> dataSource;

    @Column(nullable = false)
    private boolean searchable = false;

    @Column(nullable = false)
    private boolean sortable = false;

    @Column(nullable = false)
    private boolean indexed = false;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private boolean editable = true;

    @Column(nullable = false)
    private boolean sensitive = false;

    @Column(nullable = false)
    private boolean encrypted = false;

    @Column(name = "mask_rule")
    private String maskRule;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private String status = "enabled"; // enabled / disabled
}
