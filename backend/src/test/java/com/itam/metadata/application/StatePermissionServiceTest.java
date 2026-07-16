package com.itam.metadata.application;

import com.itam.metadata.entity.StatePermissionRule;
import com.itam.metadata.repository.StatePermissionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 状态权限解析器单元测试（Mockito，无 Spring 上下文）。
 * 覆盖：无规则仅功能权限放行、命中规则后 allowed_actions 并集、并集外动作被拒、
 * 多规则并集、角色不匹配视为无限制。
 */
@ExtendWith(MockitoExtension.class)
class StatePermissionServiceTest {

    @Mock
    private StatePermissionRuleRepository ruleRepository;
    @InjectMocks
    private StatePermissionService service;

    private UUID tenantId;
    private UUID roleId;
    private UUID assetTypeId;
    private String state = "planned";

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        assetTypeId = UUID.randomUUID();
    }

    private StatePermissionRule rule(UUID roleId, UUID assetTypeId, String state, List<String> allowed) {
        StatePermissionRule r = new StatePermissionRule();
        r.setRoleId(roleId);
        r.setAssetTypeId(assetTypeId);
        r.setLifecycleState(state);
        r.setAllowedActions(allowed);
        return r;
    }

    @Test
    void noRule_isActionAllowed_true() {
        when(ruleRepository.findByTenantIdAndDeletedFalse(tenantId)).thenReturn(List.of());

        assertTrue(service.isActionAllowed(tenantId, Set.of(roleId), assetTypeId, state, "deploy"));
        // 无限制 -> 所有动作原样返回
        assertEquals(List.of("deploy", "retire", "dispose"),
                service.filterActions(tenantId, Set.of(roleId), assetTypeId, state,
                        List.of("deploy", "retire", "dispose")));
    }

    @Test
    void ruleMatches_actionInAllowed_true() {
        when(ruleRepository.findByTenantIdAndDeletedFalse(tenantId)).thenReturn(
                List.of(rule(roleId, assetTypeId, state, List.of("deploy", "retire"))));

        assertTrue(service.isActionAllowed(tenantId, Set.of(roleId), assetTypeId, state, "deploy"));
    }

    @Test
    void ruleMatches_actionNotInAllowed_false() {
        when(ruleRepository.findByTenantIdAndDeletedFalse(tenantId)).thenReturn(
                List.of(rule(roleId, assetTypeId, state, List.of("deploy", "retire"))));

        assertFalse(service.isActionAllowed(tenantId, Set.of(roleId), assetTypeId, state, "dispose"));
    }

    @Test
    void multipleRules_unionOfAllowedActions() {
        // 两条规则同角色同状态：允许动作取并集（deploy ∪ retire），dispose 不在并集 -> 被过滤。
        when(ruleRepository.findByTenantIdAndDeletedFalse(tenantId)).thenReturn(List.of(
                rule(roleId, null, state, List.of("deploy")),
                rule(roleId, null, state, List.of("retire"))));

        List<String> allowed = service.filterActions(tenantId, Set.of(roleId), assetTypeId, state,
                List.of("deploy", "retire", "dispose"));
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains("deploy"));
        assertTrue(allowed.contains("retire"));
        assertFalse(allowed.contains("dispose"));
    }

    @Test
    void ruleRoleMismatch_noRestriction() {
        // 规则属于另一个角色 -> 当前角色无命中规则 -> 仅功能权限放行（不过滤）。
        UUID otherRole = UUID.randomUUID();
        when(ruleRepository.findByTenantIdAndDeletedFalse(tenantId)).thenReturn(
                List.of(rule(otherRole, assetTypeId, state, List.of("deploy"))));

        assertTrue(service.isActionAllowed(tenantId, Set.of(roleId), assetTypeId, state, "dispose"));
        assertEquals(List.of("deploy", "dispose"),
                service.filterActions(tenantId, Set.of(roleId), assetTypeId, state,
                        List.of("deploy", "dispose")));
    }
}
