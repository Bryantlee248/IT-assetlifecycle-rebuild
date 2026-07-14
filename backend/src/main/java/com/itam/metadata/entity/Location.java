package com.itam.metadata.entity;

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
 * 位置（租户级，最小表）。树形 parent_id 自引用，path 存祖先 id 路径便于查询。
 * MVP-1 不提供位置管理 UI，仅作为资产表单「位置选择器」数据源。
 */
@Entity
@Table(name = "locations")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Location extends TenantEntity {

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    private String code;

    @Column(columnDefinition = "text")
    private String path;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
