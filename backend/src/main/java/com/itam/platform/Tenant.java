package com.itam.platform;

import com.itam.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * 租户（平台级）。由平台管理员创建/启停。
 */
@Entity
@Table(name = "tenant")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / DISABLED

    private String description;
}
