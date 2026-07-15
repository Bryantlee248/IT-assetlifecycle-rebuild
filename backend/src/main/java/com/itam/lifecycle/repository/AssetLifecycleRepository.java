package com.itam.lifecycle.repository;

import com.itam.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 跨模块仓储：操作 assets 表，用于生命周期条件更新与资产读取。
 *
 * 并发保护：仅通过 updateStatusIfUnchanged 的 @Modifying JPQL 条件更新
 * （WHERE 含 lifecycle_status = :fromState）实现，影响行数 0 -> CONFLICT(409)。
 * 不修改 Asset 实体（不加 @Version）。
 */
@Repository
public interface AssetLifecycleRepository extends JpaRepository<Asset, UUID> {

    /** 读资产（复用 Asset 的 @Where(deleted=false) 软删过滤，跨租户 -> empty）。 */
    Optional<Asset> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * 条件更新生命周期状态：仅当当前状态仍为 fromState 时才写入 toState。
     * 返回影响行数：1=成功；0=并发已被其他流转改掉（-> CONFLICT）。
     */
    @Modifying
    @Query("""
           UPDATE Asset a
           SET a.lifecycleStatus = :toState,
               a.updatedBy        = :updatedBy,
               a.updatedAt        = CURRENT_TIMESTAMP
           WHERE a.id        = :id
             AND a.tenantId  = :tenantId
             AND a.lifecycleStatus = :fromState
             AND a.deleted   = false
           """)
    int updateStatusIfUnchanged(@Param("id") UUID id,
                                @Param("tenantId") UUID tenantId,
                                @Param("fromState") String fromState,
                                @Param("toState") String toState,
                                @Param("updatedBy") UUID updatedBy);
}
