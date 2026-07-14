package com.itam.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 租户级实体基类：在 BaseEntity 基础上强制携带 tenant_id。
 * 所有租户业务表必须继承此类，确保隔离。
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;
}
