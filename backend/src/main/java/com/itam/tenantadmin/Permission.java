package com.itam.tenantadmin;

import com.itam.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * 权限目录（平台级固定目录）。code 为功能权限码。
 */
@Entity
@Table(name = "permission")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String module;

    private String description;
}
