package com.itam.lifecycle.application;

import com.itam.asset.entity.Asset;
import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.dto.LifecycleActionResult;
import com.itam.lifecycle.entity.LifecycleEvent;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.AssetLifecycleRepository;
import com.itam.lifecycle.repository.LifecycleEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生命周期动作执行器单元测试（Mockito，无 Spring 上下文）。
 * 聚焦端到端验收点④"通过 → 写 LifecycleEvent（回填 approvalInstanceId）"：
 *  ① 审批通过回调：事件 approvalInstanceId 被回填、返回结果携带该 ID 与 eventId；
 *  ② 直转路径：approvalInstanceId 为 null，事件不带关联审批实例；
 *  ③ 并发冲突（updateStatusIfUnchanged 影响行数 0）→ CONFLICT，且不写事件。
 */
@ExtendWith(MockitoExtension.class)
class LifecycleActionExecutorTest {

    @Mock
    private AssetLifecycleRepository assetLifecycleRepository;
    @Mock
    private LifecycleEventRepository lifecycleEventRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LifecycleActionExecutor service;

    private UUID tenantId;
    private UUID assetId;
    private UUID operatorId;
    private UUID instanceId;
    private LifecycleTransition transition;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        instanceId = UUID.randomUUID();
        transition = new LifecycleTransition();
        transition.setId(UUID.randomUUID());
        transition.setTemplateId(UUID.randomUUID());
        transition.setActionCode("submit_purchase");
        transition.setActionName("采购");
        transition.setFromState("planned");
        transition.setToState("purchasing");
    }

    @Test
    void execute_writesEventWithApprovalInstanceId() {
        when(assetLifecycleRepository.updateStatusIfUnchanged(any(), any(), any(), any(), any())).thenReturn(1);
        LifecycleEvent saved = new LifecycleEvent();
        saved.setId(UUID.randomUUID());
        when(lifecycleEventRepository.save(any(LifecycleEvent.class))).thenReturn(saved);

        LifecycleActionResult result = service.execute(tenantId, operatorId, "Op", assetId, transition,
                "reason", new LinkedHashMap<>(), List.of(), instanceId);

        assertEquals("transitioned", result.getResult());
        assertEquals("planned", result.getFromState());
        assertEquals("purchasing", result.getToState());
        // 回填的审批实例 ID 随结果返回，且写入事件
        assertEquals(instanceId, result.getApprovalInstanceId());
        assertNotNull(result.getEventId());

        ArgumentCaptor<LifecycleEvent> cap = ArgumentCaptor.forClass(LifecycleEvent.class);
        verify(lifecycleEventRepository).save(cap.capture());
        assertEquals(instanceId, cap.getValue().getApprovalInstanceId());
        assertEquals(assetId, cap.getValue().getAssetId());
        assertEquals("submit_purchase", cap.getValue().getActionCode());
    }

    @Test
    void execute_directPath_nullApprovalInstanceId() {
        when(assetLifecycleRepository.updateStatusIfUnchanged(any(), any(), any(), any(), any())).thenReturn(1);
        when(lifecycleEventRepository.save(any(LifecycleEvent.class))).thenAnswer(i -> i.getArgument(0));

        LifecycleActionResult result = service.execute(tenantId, operatorId, "Op", assetId, transition,
                "reason", new LinkedHashMap<>(), List.of(), null);

        assertNull(result.getApprovalInstanceId());
        ArgumentCaptor<LifecycleEvent> cap = ArgumentCaptor.forClass(LifecycleEvent.class);
        verify(lifecycleEventRepository).save(cap.capture());
        assertNull(cap.getValue().getApprovalInstanceId());
    }

    @Test
    void execute_concurrentConflict_throwsConflict() {
        when(assetLifecycleRepository.updateStatusIfUnchanged(any(), any(), any(), any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.execute(tenantId, operatorId, "Op", assetId, transition, "reason",
                        new LinkedHashMap<>(), List.of(), instanceId));
        assertEquals(ResultCode.CONFLICT, ex.getResultCode());
        verify(lifecycleEventRepository, never()).save(any());
    }
}
