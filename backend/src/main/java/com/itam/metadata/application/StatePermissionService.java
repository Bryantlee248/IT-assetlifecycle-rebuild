package com.itam.metadata.application;

import com.itam.metadata.entity.StatePermissionRule;
import com.itam.metadata.repository.StatePermissionRuleRepository;
import com.itam.tenantadmin.Role;
import com.itam.tenantadmin.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 状态权限解析器：在功能权限（lifecycle:transition）之上，按「角色 × 资产类型 × 生命周期状态 × 动作」
 * 控制可执行动作。被 LifecycleAppService 在 getActions（过滤）与 executeAction（二次拦截）调用。
 *
 * 命中规则判定：
 *   - 命中条件 = role_id ∈ roleIds 且（asset_type_id 为空 或 等于资产类型）且 lifecycle_state == 当前状态。
 *   - 无任何命中规则 -> 仅按功能权限放行（不限制）。
 *   - 存在命中规则 -> 取所有命中规则 allowed_actions 的并集（Union），动作在并集内才允许。
 *
 * 注：LifecycleAppService 负责把 principal 的 role 码解析为 roleIds 后传入；本服务只认 roleIds。
 */
@Service
@RequiredArgsConstructor
public class StatePermissionService {

    private final StatePermissionRuleRepository ruleRepository;

    /** 当前角色是否允许执行该状态上的该动作。无命中规则 -> true（仅功能权限放行）。 */
    public boolean isActionAllowed(UUID tenantId, Set<UUID> roleIds, UUID assetTypeId,
                                   String state, String action) {
        return resolveAllowedActions(tenantId, roleIds, assetTypeId, state)
                .map(allowed -> allowed.contains(action))
                .orElse(true);
    }

    /** 过滤掉当前角色不允许执行的动作，返回可执行的动作码列表。 */
    public List<String> filterActions(UUID tenantId, Set<UUID> roleIds, UUID assetTypeId,
                                      String state, List<String> actions) {
        Set<String> allowed = resolveAllowedActions(tenantId, roleIds, assetTypeId, state).orElse(null);
        if (allowed == null) {
            return actions; // 无限制
        }
        Set<String> finalAllowed = allowed;
        return actions.stream().filter(finalAllowed::contains).toList();
    }

    /**
     * 解析允许的动作并集。
     * @return Optional.empty() 表示无任何命中规则（不限制）；否则为允许的 action 并集。
     */
    private Optional<Set<String>> resolveAllowedActions(UUID tenantId, Set<UUID> roleIds,
                                                         UUID assetTypeId, String state) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Optional.empty();
        }
        List<StatePermissionRule> rules = ruleRepository.findByTenantIdAndDeletedFalse(tenantId);
        Set<String> allowed = new LinkedHashSet<>();
        boolean anyMatch = false;
        for (StatePermissionRule rule : rules) {
            if (!roleIds.contains(rule.getRoleId())) {
                continue;
            }
            if (rule.getLifecycleState() == null || !rule.getLifecycleState().equals(state)) {
                continue;
            }
            if (rule.getAssetTypeId() != null && !rule.getAssetTypeId().equals(assetTypeId)) {
                continue;
            }
            anyMatch = true;
            if (rule.getAllowedActions() != null) {
                allowed.addAll(rule.getAllowedActions());
            }
        }
        if (!anyMatch) {
            return Optional.empty();
        }
        return Optional.of(allowed);
    }
}
