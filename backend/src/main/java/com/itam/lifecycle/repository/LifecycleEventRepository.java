package com.itam.lifecycle.repository;

import com.itam.lifecycle.entity.LifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 生命周期事件仓储。事件时间线按 createdAt 倒序，并显式排除软删行。
 */
@Repository
public interface LifecycleEventRepository extends JpaRepository<LifecycleEvent, UUID> {

    List<LifecycleEvent> findByTenantIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, UUID assetId);
}
