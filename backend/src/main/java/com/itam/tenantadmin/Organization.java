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
 * 组织（租户级）。树形结构，parent_id 自引用。
 */
@Entity
@Table(name = "organization")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends TenantEntity {

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String type; // 如 DEPT / LOCATION

    @Column(nullable = false)
    private int sort = 0;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / DISABLED
}
