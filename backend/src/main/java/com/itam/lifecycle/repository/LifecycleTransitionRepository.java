package com.itam.lifecycle.repository;

import com.itam.lifecycle.entity.LifecycleTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 生命周期流转/动作仓储。所有查询强制 tenant_id 隔离，并显式排除软删行。
 *
 * <p>旧版无排序的 {@code findByTenantIdAndTemplateId} / {@code ...AndFromState} 已删除（死方法），
 * 统一收敛到带 {@code DeletedFalse} 与 {@code OrderBy} 的显式命名。
 */
@Repository
public interface LifecycleTransitionRepository extends JpaRepository<LifecycleTransition, UUID> {

    /** GET actions：取当前状态下可执行的全部未删动作，按 sort_order ASC 稳定排序。 */
    List<LifecycleTransition> findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
            UUID tenantId, UUID templateId, String fromState);

    /** 执行校验：按 action_code + from_state 唯一定位未删流转。 */
    Optional<LifecycleTransition> findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
            UUID tenantId, UUID templateId, String actionCode, String fromState);
}
