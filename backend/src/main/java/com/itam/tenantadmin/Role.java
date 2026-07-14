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
 * 角色（租户级）。is_system=true 为内置角色，不可删。
 */
@Entity
@Table(name = "role")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Role extends TenantEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;
}
