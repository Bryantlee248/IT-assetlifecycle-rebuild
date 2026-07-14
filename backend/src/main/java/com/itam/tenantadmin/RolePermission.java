package com.itam.tenantadmin;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * 角色-权限关联（租户级）。
 */
@Entity
@Table(name = "role_permission")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission extends TenantEntity {

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private java.util.UUID roleId;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;
}
