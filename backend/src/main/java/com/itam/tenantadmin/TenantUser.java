package com.itam.tenantadmin;

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
 * 租户用户关联（租户级）。把 platform_user 关联到某租户，并默认绑定一个角色。
 */
@Entity
@Table(name = "tenant_user")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class TenantUser extends TenantEntity {

    @Column(name = "platform_user_id", nullable = false, columnDefinition = "uuid")
    private UUID platformUserId;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / DISABLED

    @Column(name = "role_id", columnDefinition = "uuid")
    private UUID roleId;
}
