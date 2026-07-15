package com.itam.lifecycle.application;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生命周期应用服务单元测试（Mockito，无 Spring 上下文）。
 * 覆盖 executeAction 全链路与查询接口（getStatus/getActions/getEvents），
 * 并显式验证软删过滤与稳定排序契约（依赖显式带 DeletedFalse/OrderBy 的仓储方法）。
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
    }

    private void stubCommonSuccessPath() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.of(transition));
    }

    @Test
    void success_transition_savesEventAndAudits() {
        stubCommonSuccessPath();
        when(assetLifecycleRepository.updateStatusIfUnchanged(any(), any(), any(), any(), any())).thenReturn(1);
        when(lifecycleEventRepository.save(any(LifecycleEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        LifecycleActionResult result = service.executeAction(tenantId, userId, "User", assetId, "deploy", req);

        assertEquals("transitioned", result.getResult());
        assertEquals("planned", result.getFromState());
        assertEquals("in_use", result.getToState());
        assertNotNull(result.getEventId());

        verify(lifecycleEventRepository).save(any(LifecycleEvent.class));
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(actionCaptor.capture(), eq("ASSET"), eq(assetId.toString()), any());
        assertEquals("LIFECYCLE_TRANSITION", actionCaptor.getValue());
    }

    @Test
    void approval_required_returnsApprovalResult() {
        transition.setRequireApproval(true);
        stubCommonSuccessPath();

        LifecycleActionResult result = service.executeAction(tenantId, userId, "User", assetId, "deploy", req);

        assertEquals("approval_required", result.getResult());
        assertNull(result.getFromState());
        assertNull(result.getToState());
        assertNull(result.getEventId());

        verify(lifecycleEventRepository, never()).save(any());
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(actionCaptor.capture(), eq("ASSET"), eq(assetId.toString()), any());
        assertEquals("LIFECYCLE_APPROVAL_REQUIRED", actionCaptor.getValue());
    }

    @Test
    void illegal_action_throws422WithMessage() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertTrue(ex.getMessage().contains("非法生命周期动作"));

        verify(lifecycleEventRepository, never()).save(any());
    }

    @Test
    void guard_failure_throws422() {
        stubCommonSuccessPath();
        doThrow(new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "守卫校验失败：缺少必填字段[x]"))
                .when(guardEvaluator).evaluate(any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        verify(lifecycleEventRepository, never()).save(any());
    }

    @Test
    void concurrent_conflict_throwsConflict() {
        stubCommonSuccessPath();
        when(assetLifecycleRepository.updateStatusIfUnchanged(any(), any(), any(), any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", assetId, "deploy", req));
        assertEquals(ResultCode.CONFLICT, ex.getResultCode());
        assertEquals("生命周期状态已被其他操作变更，请刷新后重试", ex.getMessage());
        verify(lifecycleEventRepository, never()).save(any());
    }

    @Test
    void asset_not_found_throwsAssetNotFound() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", assetId, "deploy", req));
        assertEquals(ResultCode.ASSET_NOT_FOUND, ex.getResultCode());
    }

    /**
     * getActions：依赖带 DeletedFalse + OrderBySortOrderAsc 的查询，
     * 返回结果不含软删流转，且按 sort_order 升序排列。
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

        // 软删流转（ghost）不放入返回列表，模拟查询层已排除。
        LifecycleTransition ghost = new LifecycleTransition();
        ghost.setActionCode("ghost");
        ghost.setFromState("planned");
        ghost.setToState("in_use");
        ghost.setSortOrder(0);
        ghost.setDeleted(true);

        when(lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, "planned"))
                .thenReturn(List.of(start, deploy));

        List<LifecycleActionResponse> actions = service.getActions(tenantId, assetId);

        assertEquals(2, actions.size());
        // 验证按 sort_order 升序
        assertEquals("start", actions.get(0).getActionCode());
        assertEquals("deploy", actions.get(1).getActionCode());
        // 验证软删流转被排除
        boolean hasGhost = actions.stream()
                .map(LifecycleActionResponse::getActionCode)
                .anyMatch("ghost"::equals);
        assertFalse(hasGhost);
        verify(lifecycleTransitionRepository)
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, templateId, "planned");
    }

    /**
     * executeAction：流转被软删后，定位查询（AndDeletedFalse）返回 empty，
     * 服务判定为非法动作 -> BUSINESS_RULE_VIOLATION。
     */
    @Test
    void executeAction_softDeletedTransition_throws422() {
        when(assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)).thenReturn(Optional.of(asset));
        when(templateResolver.resolve(tenantId, asset)).thenReturn(template);
        when(lifecycleTransitionRepository.findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                tenantId, templateId, "deploy", "planned")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executeAction(tenantId, userId, "User", assetId, "deploy", req));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        verify(lifecycleEventRepository, never()).save(any());
    }

    /**
     * getEvents：依赖带 DeletedFalse 的查询，返回时间线不含软删事件。
     */
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

    /**
     * getStatus：状态名称取自未软删状态（依赖带 DeletedFalse 的状态查询）。
     */
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
