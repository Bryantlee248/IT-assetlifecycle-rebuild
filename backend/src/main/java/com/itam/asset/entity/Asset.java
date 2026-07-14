package com.itam.asset.entity;

import com.itam.common.base.TenantEntity;
import com.itam.asset.constants.HotspotColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * 资产主对象（租户级）。
 * 全部热点物理列 + attributes(JSONB) 扩展字段；生命周期初始状态默认 planned，由服务端控制。
 */
@Entity
@Table(name = "assets")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Asset extends TenantEntity {

    public static final String LIFECYCLE_STATUS_PLANNED = "planned";

    @Column(name = "asset_no", nullable = false)
    private String assetNo;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(name = "asset_kind", nullable = false)
    private String assetKind;

    @Column(name = "asset_type_id", nullable = false, columnDefinition = "uuid")
    private UUID assetTypeId;

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus;

    @Column(name = "owner_user_id", columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(name = "owner_org_id", columnDefinition = "uuid")
    private UUID ownerOrgId;

    @Column(name = "location_id", columnDefinition = "uuid")
    private UUID locationId;

    @Column(name = "cost_center_id", columnDefinition = "uuid")
    private UUID costCenterId;

    @Column(name = "responsible_user_id", columnDefinition = "uuid")
    private UUID responsibleUserId;

    @Column(name = "serial_no")
    private String serialNo;

    private String brand;

    private String model;

    private String vendor;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "license_end_date")
    private LocalDate licenseEndDate;

    @Column(name = "source_type", nullable = false)
    private String sourceType = "manual";

    @Column(name = "sync_source")
    private String syncSource;

    @Column(name = "metadata_version", nullable = false)
    private int metadataVersion = 1;

    @Column(nullable = false)
    private String status = "active";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = new java.util.LinkedHashMap<>();

    /**
     * 读取物理列值（按列名）。非热点物理列返回 null。
     */
    public Object getPhysicalValue(String column) {
        return switch (column) {
            case "asset_no" -> assetNo;
            case "asset_name" -> assetName;
            case "asset_kind" -> assetKind;
            case "asset_type_id" -> assetTypeId;
            case "lifecycle_status" -> lifecycleStatus;
            case "owner_user_id" -> ownerUserId;
            case "owner_org_id" -> ownerOrgId;
            case "location_id" -> locationId;
            case "cost_center_id" -> costCenterId;
            case "responsible_user_id" -> responsibleUserId;
            case "serial_no" -> serialNo;
            case "brand" -> brand;
            case "model" -> model;
            case "vendor" -> vendor;
            case "warranty_end_date" -> warrantyEndDate;
            case "license_end_date" -> licenseEndDate;
            default -> null;
        };
    }

    /**
     * 写入物理列值（按列名），自动做类型转换（UUID / LocalDate / String）。
     */
    public void setPhysicalValue(String column, Object value) {
        if (value == null) {
            return;
        }
        switch (column) {
            case "asset_no" -> assetNo = asString(value);
            case "asset_name" -> assetName = asString(value);
            case "asset_kind" -> assetKind = asString(value);
            case "asset_type_id" -> assetTypeId = asUuid(value);
            case "lifecycle_status" -> lifecycleStatus = asString(value);
            case "owner_user_id" -> ownerUserId = asUuid(value);
            case "owner_org_id" -> ownerOrgId = asUuid(value);
            case "location_id" -> locationId = asUuid(value);
            case "cost_center_id" -> costCenterId = asUuid(value);
            case "responsible_user_id" -> responsibleUserId = asUuid(value);
            case "serial_no" -> serialNo = asString(value);
            case "brand" -> brand = asString(value);
            case "model" -> model = asString(value);
            case "vendor" -> vendor = asString(value);
            case "warranty_end_date" -> warrantyEndDate = asLocalDate(value);
            case "license_end_date" -> licenseEndDate = asLocalDate(value);
            default -> { /* 非热点物理列忽略 */ }
        };
    }

    /** 该列是否为热点物理列（用于拆分逻辑）。 */
    public static boolean isHotspotColumn(String column) {
        return HotspotColumn.ALL.contains(column);
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static UUID asUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        return UUID.fromString(v.toString());
    }

    private static LocalDate asLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        return LocalDate.parse(v.toString());
    }
}
