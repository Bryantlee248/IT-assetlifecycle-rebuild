package com.itam.approval.application;

import com.itam.approval.dto.ApprovalInstanceResponse;
import com.itam.approval.entity.ApprovalInstance;
import com.itam.approval.entity.ApprovalNode;
import com.itam.approval.entity.ApprovalTask;
import com.itam.approval.entity.ApprovalTemplate;
import com.itam.approval.entity.ApproverType;
import com.itam.approval.entity.InstanceStatus;
import com.itam.approval.entity.TaskStatus;
import com.itam.approval.repository.ApprovalInstanceRepository;
import com.itam.approval.repository.ApprovalNodeRepository;
import com.itam.approval.repository.ApprovalTaskRepository;
import com.itam.approval.repository.ApprovalTemplateRepository;
import com.itam.asset.entity.Asset;
import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.application.LifecycleActionExecutor;
import com.itam.lifecycle.dto.LifecycleActionResult;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import com.itam.notification.application.NotificationService;
import com.itam.notification.entity.NotificationType;
import com.itam.platform.PlatformUser;
import com.itam.tenantadmin.TenantUser;
import com.itam.tenantadmin.TenantUserRepository;
import com.itam.platform.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批应用服务单元测试（Mockito，无 Spring 上下文）。
 * 覆盖 MVP-3 审批规则与端到端契约：
 *  ① 无驳回意见不能驳回（null / 空白均 422）；② 非审批人即使有权限也不能审批；
 *  ③ ROLE 扇出首批生效其余取消；④ 末节点通过 -> 回调执行器（状态流转）+ 通知申请人；
 *  ⑤ 驳回 -> 实例 REJECTED、不调用执行器、通知申请人（含意见）；
 *  ⑥ 创建审批实例：USER 单任务、ROLE 扇出多任务，各发待办通知；状态保持 PENDING。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalInstanceRepository instanceRepository;
    @Mock
    private ApprovalNodeRepository nodeRepository;
    @Mock
    private ApprovalTaskRepository taskRepository;
    @Mock
    private ApprovalTemplateRepository templateRepository;
    @Mock
    private LifecycleTransitionRepository transitionRepository;
    @Mock
    private TenantUserRepository tenantUserRepository;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private LifecycleActionExecutor lifecycleActionExecutor;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalService service;

    private UUID tenantId;
    private UUID templateId;
    private UUID transitionId;
    private UUID assetId;
    private UUID applicantId;
    private UUID approverA;
    private UUID approverB;
    private UUID approverC;
    private UUID roleId;
    private UUID instanceId;
    private Asset asset;
    private LifecycleTransition transition;
    private ApprovalTemplate template;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        transitionId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        applicantId = UUID.randomUUID();
        approverA = UUID.randomUUID();
        approverB = UUID.randomUUID();
        approverC = UUID.randomUUID();
        roleId = UUID.randomUUID();
        instanceId = UUID.randomUUID();

        asset = new Asset();
        asset.setAssetName("Server-1");

        transition = new LifecycleTransition();
        transition.setId(transitionId);
        transition.setActionName("采购");
        transition.setFromState("planned");
        transition.setToState("purchasing");
        transition.setApprovalTemplateId(templateId);

        template = new ApprovalTemplate();
        template.setId(templateId);
        template.setName("采购审批模板");
    }

    // ============================ 驳回规则 ============================

    @Test
    void reject_nullComment_throws422() {
        // 驳回意见为 null -> BUSINESS_RULE_VIOLATION，且不触及执行器/通知。
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(tenantId, approverA, "A", instanceId, null));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertTrue(ex.getMessage().contains("驳回必须填写意见"));
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reject_blankComment_throws422() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(tenantId, approverA, "A", instanceId, "   "));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertTrue(ex.getMessage().contains("驳回必须填写意见"));
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ============================ 非审批人拦截 ============================

    @Test
    void approve_byNonApprover_throws422() {
        ApprovalInstance instance = baseInstance(InstanceStatus.PENDING);
        ApprovalTask task = pendingTask(instanceId, approverA, 1);

        when(instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, instanceId))
                .thenReturn(java.util.Optional.of(instance));
        when(taskRepository.findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, instanceId, 1))
                .thenReturn(List.of(task));
        // 当前用户是"陌生人"，不是该 pending 任务的审批人
        UUID stranger = UUID.randomUUID();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(tenantId, stranger, "Stranger", instanceId, "ok"));
        assertEquals(ResultCode.BUSINESS_RULE_VIOLATION, ex.getResultCode());
        assertTrue(ex.getMessage().contains("您不是该审批任务的审批人"));
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    // ============================ 末节点通过 ============================

    @Test
    void approve_lastNode_invokesExecutorAndNotifiesApplicant() {
        ApprovalInstance instance = baseInstance(InstanceStatus.PENDING);
        instance.setReason("because");
        ApprovalTask task = pendingTask(instanceId, approverA, 1);

        when(instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, instanceId))
                .thenReturn(java.util.Optional.of(instance));
        when(nodeRepository.existsByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(
                tenantId, templateId, 2)).thenReturn(false); // 末节点
        when(taskRepository.findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, instanceId, 1))
                .thenReturn(List.of(task));
        when(taskRepository.save(any(ApprovalTask.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(
                tenantId, instanceId)).thenReturn(List.of(task));
        when(transitionRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, transitionId))
                .thenReturn(java.util.Optional.of(transition));
        when(platformUserRepository.findById(approverA)).thenReturn(java.util.Optional.of(platformUser("A")));
        when(instanceRepository.save(any(ApprovalInstance.class))).thenAnswer(i -> i.getArgument(0));
        when(lifecycleActionExecutor.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleActionResult("transitioned", "planned", "purchasing", instanceId, UUID.randomUUID()));

        ApprovalInstanceResponse resp = service.approve(tenantId, approverA, "Approver", instanceId, "ok");

        // 末节点 -> 回调执行器完成状态流转（lifecycle_status 仅经执行器变更）
        verify(lifecycleActionExecutor).execute(eq(tenantId), eq(approverA), eq("Approver"), eq(assetId),
                eq(transition), eq("because"), any(), any(), eq(instanceId));
        // 通知申请人审批通过
        ArgumentCaptor<UUID> receiverCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> contentCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(tenantId), receiverCap.capture(), eq(NotificationType.APPROVAL_APPROVED),
                eq("APPROVAL"), eq(instanceId), any(), contentCap.capture());
        assertEquals(applicantId, receiverCap.getValue());
        assertTrue(contentCap.getValue().contains("采购"));
        // 实例与任务状态正确
        assertEquals(InstanceStatus.APPROVED, resp.getStatus());
        assertEquals(TaskStatus.APPROVED, task.getStatus());
    }

    // ============================ ROLE 扇出首批生效 ============================

    @Test
    void approve_roleFanout_firstApproverCancelsOthers() {
        ApprovalInstance instance = baseInstance(InstanceStatus.PENDING);
        ApprovalTask t1 = pendingTask(instanceId, approverA, 1);
        ApprovalTask t2 = pendingTask(instanceId, approverB, 1);
        ApprovalTask t3 = pendingTask(instanceId, approverC, 1);

        when(instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, instanceId))
                .thenReturn(java.util.Optional.of(instance));
        when(nodeRepository.existsByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(
                tenantId, templateId, 2)).thenReturn(false); // 末节点
        when(taskRepository.findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, instanceId, 1))
                .thenReturn(List.of(t1, t2, t3));
        when(taskRepository.save(any(ApprovalTask.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(
                tenantId, instanceId)).thenReturn(List.of(t1, t2, t3));
        when(transitionRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, transitionId))
                .thenReturn(java.util.Optional.of(transition));
        when(platformUserRepository.findById(any())).thenReturn(java.util.Optional.of(platformUser("X")));
        when(instanceRepository.save(any(ApprovalInstance.class))).thenAnswer(i -> i.getArgument(0));
        when(lifecycleActionExecutor.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleActionResult("transitioned", "planned", "purchasing", instanceId, UUID.randomUUID()));

        service.approve(tenantId, approverA, "A", instanceId, "ok");

        // 首批生效：审批人 A 通过，其余 B/C 的 pending 任务取消
        assertEquals(TaskStatus.APPROVED, t1.getStatus());
        assertEquals(TaskStatus.CANCELLED, t2.getStatus());
        assertEquals(TaskStatus.CANCELLED, t3.getStatus());
        verify(lifecycleActionExecutor).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ============================ 驳回端到端 ============================

    @Test
    void reject_validComment_rejectsInstanceAndNotifiesNoExecutor() {
        ApprovalInstance instance = baseInstance(InstanceStatus.PENDING);
        ApprovalTask task = pendingTask(instanceId, approverA, 1);
        String comment = "不符合采购规范";

        when(instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, instanceId))
                .thenReturn(java.util.Optional.of(instance));
        when(taskRepository.findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, instanceId, 1))
                .thenReturn(List.of(task));
        when(taskRepository.save(any(ApprovalTask.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(
                tenantId, instanceId)).thenReturn(List.of(task));
        when(transitionRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, transitionId))
                .thenReturn(java.util.Optional.of(transition));
        when(platformUserRepository.findById(approverA)).thenReturn(java.util.Optional.of(platformUser("A")));
        when(instanceRepository.save(any(ApprovalInstance.class))).thenAnswer(i -> i.getArgument(0));

        ApprovalInstanceResponse resp = service.reject(tenantId, approverA, "A", instanceId, comment);

        // 驳回：实例 REJECTED；不调用执行器（资产状态不变、不写事件）；通知申请人含意见
        assertEquals(InstanceStatus.REJECTED, resp.getStatus());
        assertEquals(TaskStatus.REJECTED, task.getStatus());
        verify(lifecycleActionExecutor, never()).execute(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<UUID> receiverCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> contentCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(tenantId), receiverCap.capture(), eq(NotificationType.APPROVAL_REJECTED),
                eq("APPROVAL"), eq(instanceId), any(), contentCap.capture());
        assertEquals(applicantId, receiverCap.getValue());
        assertTrue(contentCap.getValue().contains(comment));
    }

    // ============================ 创建审批实例 ============================

    @Test
    void createInstance_userNode_createsPendingInstanceAndTaskAndNotifies() {
        ApprovalNode node = new ApprovalNode();
        node.setTemplateId(templateId);
        node.setNodeOrder(1);
        node.setApproverType(ApproverType.USER);
        node.setApproverUserId(approverA);

        when(nodeRepository.findByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(tenantId, templateId, 1))
                .thenReturn(java.util.Optional.of(node));
        when(instanceRepository.save(any(ApprovalInstance.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ApprovalInstance created = service.createInstance(
                tenantId, asset, transition, template, applicantId, "Applicant", "apply");

        // 创建即 PENDING，资产状态不变、不写事件
        assertEquals(InstanceStatus.PENDING, created.getStatus());
        assertEquals(1, created.getCurrentNodeOrder());
        // USER 节点 -> 1 个 pending 任务，审批人精确为 approverA
        verify(taskRepository).saveAll(argThat((List<ApprovalTask> tasks) -> {
            if (tasks.size() != 1) return false;
            ApprovalTask t = tasks.get(0);
            return approverA.equals(t.getApproverId())
                    && t.getNodeOrder() == 1
                    && t.getStatus() == TaskStatus.PENDING
                    && t.getApproverType() == ApproverType.USER;
        }));
        // 给审批人发 1 条待办通知
        verify(notificationService).create(eq(tenantId), eq(approverA), eq(NotificationType.APPROVAL_TASK),
                eq("APPROVAL"), eq(created.getId()), any(), any());
    }

    @Test
    void createInstance_roleNode_fansOutToThreeTasks() {
        ApprovalNode node = new ApprovalNode();
        node.setTemplateId(templateId);
        node.setNodeOrder(1);
        node.setApproverType(ApproverType.ROLE);
        node.setApproverRoleId(roleId);

        TenantUser tuA = tenantUser(approverA);
        TenantUser tuB = tenantUser(approverB);
        TenantUser tuC = tenantUser(approverC);

        when(nodeRepository.findByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(tenantId, templateId, 1))
                .thenReturn(java.util.Optional.of(node));
        when(tenantUserRepository.findByTenantIdAndRoleIdAndStatusAndDeletedFalse(tenantId, roleId, "ACTIVE"))
                .thenReturn(List.of(tuA, tuB, tuC));
        when(instanceRepository.save(any(ApprovalInstance.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ApprovalInstance created = service.createInstance(
                tenantId, asset, transition, template, applicantId, "Applicant", "apply");

        assertEquals(InstanceStatus.PENDING, created.getStatus());
        // ROLE 扇出 -> 3 个审批人各 1 个任务与通知
        verify(taskRepository).saveAll(argThat((List<ApprovalTask> tasks) -> tasks.size() == 3));
        ArgumentCaptor<UUID> receiverCap = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, org.mockito.Mockito.times(3)).create(eq(tenantId), receiverCap.capture(),
                eq(NotificationType.APPROVAL_TASK), eq("APPROVAL"), eq(created.getId()), any(), any());
        assertTrue(receiverCap.getAllValues().containsAll(Set.of(approverA, approverB, approverC)));
    }

    // ============================ 辅助 ============================

    private ApprovalInstance baseInstance(InstanceStatus status) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(instanceId);
        instance.setTenantId(tenantId);
        instance.setTemplateId(templateId);
        instance.setAssetId(assetId);
        instance.setTransitionId(transitionId);
        instance.setActionCode("submit_purchase");
        instance.setFromState("planned");
        instance.setToState("purchasing");
        instance.setApplicantId(applicantId);
        instance.setApplicantName("Applicant");
        instance.setReason("because");
        instance.setStatus(status);
        instance.setCurrentNodeOrder(1);
        return instance;
    }

    private ApprovalTask pendingTask(UUID instId, UUID approverId, int nodeOrder) {
        ApprovalTask t = new ApprovalTask();
        t.setId(UUID.randomUUID());
        t.setInstanceId(instId);
        t.setNodeOrder(nodeOrder);
        t.setApproverId(approverId);
        t.setApproverType(ApproverType.USER);
        t.setStatus(TaskStatus.PENDING);
        return t;
    }

    private TenantUser tenantUser(UUID platformUserId) {
        TenantUser tu = new TenantUser();
        tu.setPlatformUserId(platformUserId);
        tu.setStatus("ACTIVE");
        tu.setRoleId(roleId);
        return tu;
    }

    private PlatformUser platformUser(String name) {
        PlatformUser u = new PlatformUser();
        u.setDisplayName(name);
        return u;
    }
}
