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
 * 表单配置（租户级，每资产类型一套）。schema_json 存分组/列数/字段顺序。
 */
@Entity
@Table(name = "form_schemas")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class FormSchema extends TenantEntity {

    @Column(name = "asset_type_id", nullable = false, columnDefinition = "uuid")
    private UUID assetTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> schemaJson;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean enabled = true;
}
