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
 * 字段权限规则（租户级）。MVP-1 本表不喂种子数据，字段权限由内存默认矩阵实现；
 * 预留以便 MVP-3 配置页面平滑接入。
 */
@Entity
@Table(name = "field_permission_rules")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class FieldPermissionRule extends TenantEntity {

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "asset_type_id", columnDefinition = "uuid")
    private UUID assetTypeId;

    @Column(name = "field_code", nullable = false)
    private String fieldCode;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private boolean editable = false;

    @Column(nullable = false)
    private boolean masked = false;

    @Column(nullable = false)
    private boolean exportable = false;

    @Column(name = "mask_rule")
    private String maskRule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_rule", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditionRule = java.util.Map.of();

    @Column(nullable = false)
    private boolean enabled = true;
}
