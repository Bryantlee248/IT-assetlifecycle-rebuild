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

import java.util.List;
import java.util.UUID;

/**
 * 状态权限规则（租户级）。在功能权限之上，按「角色 × 资产类型 × 生命周期状态 × 动作」控制可执行动作。
 * asset_type_id 为空表示全类型生效；allowed_actions 为允许的动作码 JSONB 数组。
 * 命中条件 = role_id 且（asset_type_id 为空 或 等于资产类型）且 lifecycle_state == 当前状态。
 * 多规则命中时取 allowed_actions 的并集（Union）：任一规则允许即可执行。
 */
@Entity
@Table(name = "state_permission_rules")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class StatePermissionRule extends TenantEntity {

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "asset_type_id", columnDefinition = "uuid")
    private UUID assetTypeId;

    @Column(name = "lifecycle_state", nullable = false, length = 64)
    private String lifecycleState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> allowedActions = java.util.List.of();

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
