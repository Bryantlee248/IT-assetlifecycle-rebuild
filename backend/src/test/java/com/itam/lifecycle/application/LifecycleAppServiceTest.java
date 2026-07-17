package com.itam.lifecycle.application;

import com.itam.approval.application.ApprovalService;
import com.itam.approval.entity.ApprovalInstance;
import com.itam.approval.entity.ApprovalTemplate;
import com.itam.approval.repository.ApprovalTemplateRepository;
import com.itam.asset.entity.Asset;
import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.domain.LifecycleGuardEvaluator;
import com.itam.lifecycle.domain.TemplateResolver;
import com.itam.lifecycle.dto.ExecuteActionRequest;
import com.itam.lifecycle.dto.LifecycleActionResponse;
import com.itam.lifecycle.dto.LifecycleActionResult;
import com.itam.lifecycle.dto.LifecycleEventResponse;
import com.itam.lifecycle.entity.LifecycleEvent;
import com.itam.lifecycle.entity.LifecycleState;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.AssetLifecycleRepository;
import com.itam.lifecycle.repository.LifecycleEventRepository;
import com.itam.lifecycle.repository.LifecycleStateRepository;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import com.itam.metadata.application.StatePermissionService;
import com.itam.tenantadmin.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生命周期应用服务单元测试（Mockito，无 Spring 上下文）。
 * 覆盖 executeAction 全链路与查询接口（getStatus/getActions/getEvents），
 * 并验证：状态权限二次拦截、需审批分支创建实例、直转路径委派给 LifecycleActionExecutor。
 */
@ExtendWith(MockitoExtension.class)
class LifecycleAppServiceTest {

    @Mock
    private AssetLifecycleRepository assetLifecycleRepository;
    @Mock
    private LifecycleStateRepository lifecycleStateRepository;
    @Mock
    private LifecycleTransitionRepository lifecycleTransitionRepository;
    @Mock
    private LifecycleEventRepository lifecycleEventRepository;
    @Mock
    private TemplateResolver templateResolver;
    @Mock
    private LifecycleGuardEvaluator guardEvaluator;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ApprovalTemplateRepository approvalTemplateRepository;
    @Mock
    private StatePermissionService statePermissionService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private LifecycleActionExecutor lifecycleActionExecutor;

    @InjectMocks
    private LifecycleAppService service;

    private UUID tenantId;
    private UUID userId;
    private UUID assetId;
    private UUID templateId;
    private UUID transitionId;
    private Asset asset;
    private LifecycleTemplate template;
    private LifecycleTransition transition;
    private ExecuteActionRequest req;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        transitionId = UUID.randomUUID();

        asset = new Asset();
        asset.setTenantId(tenantId);
        asset.setAssetKind("tangible");
        asset.setAssetTypeId(UUID.randomUUID());
        asset.setLifecycleStatus("planned");

        template = new LifecycleTemplate();
        template.setId(templateId);
        template.setName("tpl");
        template.setAssetKind("tangible");
        template.setEnabled(true);

        transition = new LifecycleTransition();
        transition.setId(transitionId);
        transition.setActionCode("deploy");
        transition.setActionName("部署");
        transition.setFromState("planned");
        transition.setToState("in_use");
        transition.setRequireApproval(false);
        transition.setRequireAttachment(false);
        transition.setGuardRule(new LinkedHashMap<>());

        req = new ExecuteActionRequest("reason", Map.of(), List.of());

        // 状态权限默认放行（无规则），避免影响主链路断言。
        // 用 lenient 避免部分不触达该调用的用例被 Mockito 严格桩判定为"多余桩"而失败。
        lenient().when(statePermissionService.isActionAllowed(any(), any(), any(), any(), any())).thenReturn(true);
    }

    private void stubCommonSuccessPath() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.of(transition));
    }

    @Test
    void success_transition_delegatesToExecutor() {
        stubCommonSuccessPath();
        when(lifecycleActionExecutor.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleActionResult("transitioned", "planned", "in_use", null, UUID.randomUUID()));

        LifecycleActionResult result = service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req);

        assertEquals("transitioned", result.getResult());
        assertEquals("planned", result.getFromState());
        assertEquals("in_use", result.getToState());
        assertNotNull(result.getEventId());
        verify(lifecycleActionExecutor).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(lifecycleEventRepository, never()).save(any());
    }

    @Test
    void approval_required_createsInstanceAndReturnsApprovalResult() {
        transition.setRequireApproval(true);
        stubCommonSuccessPath();

        ApprovalTemplate approvalTemplate = new ApprovalTemplate();
        approvalTemplate.setId(UUID.randomUUID());
        when(approvalTemplateRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, transition.getApprovalTemplateId()))
                .thenReturn(Optional.of(approvalTemplate));

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(UUID.randomUUID());
        when(approvalService.createInstance(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(instance);

        LifecycleActionResult result = service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req);

        assertEquals("approval_required", result.getResult());
        assertNull(result.getFromState());
        assertNull(result.getToState());
        assertNull(result.getEventId());
        assertNotNull(result.getApprovalInstanceId());
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(lifecycleEventRepository, never()).save(any());
        verify(approvalService).createInstance(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void illegal_action_throws422WithMessage() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertTrue(ex.getMessage().contains("非法生命周期动作"));
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guard_failure_throws422() {
        stubCommonSuccessPath();
        doThrow(new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "守卫校验失败：缺少必填字段[x]"))
                .when(guardEvaluator).evaluate(any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void state_permission_denied_throws422() {
        stubCommonSuccessPath();
        when(statePermissionService.isActionAllowed(any(), any(), any(), any(), any())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void concurrent_conflict_throwsConflict() {
        stubCommonSuccessPath();
        when(lifecycleActionExecutor.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ResultCode.CONFLICT, "生命周期状态已被其他操作变更，请刷新后重试"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.CONFLICT, ex.getResultCode());
    }

    @Test
    void asset_not_found_throwsAssetNotFound() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.ASSET_NOT_FOUND, ex.getResultCode());
    }

    /**
     * getActions：依赖带 DeletedFalse + OrderBySortOrderAsc 的查询，
     * 返回结果不含软删流转，且按 sort_order 升序排列；并经状态权限过滤。
     */
    @Test
    void getActions_excludesDeletedAndOrdersBySortOrder() {
        asset.setLifecycleStatus("planned");
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);

        LifecycleState inUse = new LifecycleState();
        inUse.setStateCode("in_use");
        inUse.setStateName("运行中");
        inUse.setDeleted(false);
        when(lifecycleStateRepository.findByTenantIdAndTemplateIdAndDeletedFalse(tenantId, templateId))
                .thenReturn(List.of(inUse));

        LifecycleTransition deploy = new LifecycleTransition();
        deploy.setActionCode("deploy");
        deploy.setActionName("部署");
        deploy.setFromState("planned");
        deploy.setToState("in_use");
        deploy.setSortOrder(2);
        deploy.setRequireApproval(false);
        deploy.setRequireAttachment(false);
        deploy.setGuardRule(new LinkedHashMap<>());

        LifecycleTransition start = new LifecycleTransition();
        start.setActionCode("start");
        start.setActionName("启动");
        start.setFromState("planned");
        start.setToState("in_use");
        start.setSortOrder(1);
        start.setRequireApproval(false);
        start.setRequireAttachment(false);
        start.setGuardRule(new LinkedHashMap<>());

        when(lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, "planned"))
                .thenReturn(List.of(start, deploy));

        List<LifecycleActionResponse> actions = service.getActions(tenantId, Set.of(), assetId);

        assertEquals(2, actions.size());
        assertEquals("start", actions.get(0).getActionCode());
        assertEquals("deploy", actions.get(1).getActionCode());
        verify(lifecycleTransitionRepository)
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, "planned");
    }

    @Test
    void getActions_filtersByStatePermission() {
        asset.setLifecycleStatus("planned");
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleStateRepository.findByTenantIdAndTemplateIdAndDeletedFalse(tenantId, templateId))
                .thenReturn(List.of());

        LifecycleTransition retire = new LifecycleTransition();
        retire.setActionCode("retire");
        retire.setActionName("退役");
        retire.setFromState("planned");
        retire.setToState("scrapped");
        retire.setSortOrder(1);
        retire.setRequireApproval(false);
        retire.setRequireAttachment(false);
        retire.setGuardRule(new LinkedHashMap<>());
        when(lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, "planned"))
                .thenReturn(List.of(retire));

        // 状态权限：retire 不在允许集合内 -> 被过滤
        when(statePermissionService.isActionAllowed(any(), any(), any(), any(), any())).thenReturn(false);

        List<LifecycleActionResponse> actions = service.getActions(tenantId, Set.of(), assetId);
        assertTrue(actions.isEmpty());
    }

    @Test
    void executeAction_softDeletedTransition_throws422() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", Set.of(), assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getEvents_excludesDeleted() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));

        UUID aliveId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();

        LifecycleEvent alive = new LifecycleEvent();
        alive.setId(aliveId);
        alive.setAssetId(assetId);
        alive.setTemplateId(templateId);
        alive.setActionCode("deploy");
        alive.setActionName("部署");
        alive.setFromState("planned");
        alive.setToState("in_use");
        alive.setOperatorId(userId);
        alive.setDeleted(false);

        LifecycleEvent deleted = new LifecycleEvent();
        deleted.setId(deletedId);
        deleted.setActionCode("dispose");
        deleted.setDeleted(true);

        when(lifecycleEventRepository.findByTenantIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, assetId))
                .thenReturn(List.of(alive));

        List<LifecycleEventResponse> events = service.getEvents(tenantId, assetId);

        assertEquals(1, events.size());
        assertEquals(aliveId, events.get(0).getId());
        boolean hasDeleted = events.stream().anyMatch(e -> deletedId.equals(e.getId()));
        assertFalse(hasDeleted);
    }

    @Test
    void getStatus_usesNonDeletedState() {
        asset.setLifecycleStatus("planned");
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);

        LifecycleState planned = new LifecycleState();
        planned.setStateCode("planned");
        planned.setStateName("规划/申请");
        planned.setDeleted(false);

        when(lifecycleStateRepository.findByTenantIdAndTemplateIdAndStateCodeAndDeletedFalse(
                tenantId, templateId, "planned")).thenReturn(Optional.of(planned));

        var status = service.getStatus(tenantId, assetId);
        assertEquals("planned", status.getCurrentState());
        assertEquals("规划/申请", status.getCurrentStateName());
    }
}
