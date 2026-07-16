package com.itam.approval.application;

import com.itam.approval.dto.ApprovalInstanceResponse;
import com.itam.approval.dto.ApprovalInstanceSummary;
import com.itam.approval.dto.ApprovalTaskResponse;
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
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import com.itam.notification.application.NotificationService;
import com.itam.notification.entity.NotificationType;
import com.itam.platform.PlatformUser;
import com.itam.platform.PlatformUserRepository;
import com.itam.tenantadmin.TenantUser;
import com.itam.tenantadmin.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审批应用服务：审批实例的创建、查询、通过、驳回，以及 ROLE 审批人扇出与通知联动。
 *
 * 依赖方向（无环）：
 *   LifecycleAppService ──▶ ApprovalService ──▶ LifecycleActionExecutor（末节点回调真正流转）
 *                                       └──▶ NotificationService（建实例/通过/驳回/流转时发通知）
 *
 * 红线：
 *   - 资产状态 ONLY 经 LifecycleActionExecutor 变更（approve 末节点回调）。
 *   - 非审批人即使有权限也不能审批：approve/reject 仅作用于 approverId==当前用户 的 pending 任务。
 *   - 所有新表带 tenant_id；审批/通知均强隔离。
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalNodeRepository nodeRepository;
    private final ApprovalTaskRepository taskRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final LifecycleTransitionRepository transitionRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PlatformUserRepository platformUserRepository;
    private final NotificationService notificationService;
    private final LifecycleActionExecutor lifecycleActionExecutor;
    private final AuditLogService auditLogService;

    // =========================================================================
    // 创建审批实例（需审批的生命周期动作触发）
    // =========================================================================

    /**
     * 创建审批实例 + 首个节点任务 + 待办通知。
     * 资产状态不变、不写事件；返回实例供调用方回填 approvalInstanceId。
     */
    @Transactional
    public ApprovalInstance createInstance(UUID tenantId, Asset asset, LifecycleTransition transition,
                                            ApprovalTemplate template, UUID applicantId,
                                            String applicantName, String reason) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setTenantId(tenantId);
        instance.setTemplateId(template.getId());
        instance.setAssetId(asset.getId());
        instance.setTransitionId(transition.getId());
        instance.setActionCode(transition.getActionCode());
        instance.setActionName(transition.getActionName());
        instance.setFromState(transition.getFromState());
        instance.setToState(transition.getToState());
        instance.setApplicantId(applicantId);
        instance.setApplicantName(applicantName);
        instance.setReason(reason);
        instance.setStatus(InstanceStatus.PENDING);
        instance.setCurrentNodeOrder(1);
        instance.setTitle(buildTitle(asset, transition));
        instance.setCreatedBy(applicantId);
        instance.setUpdatedBy(applicantId);
        ApprovalInstance saved = instanceRepository.save(instance);

        // 解析节点审批人并建任务（ROLE -> fan-out 为多名成员各 1 个 pending 任务）
        List<ApprovalTask> tasks = buildNodeTasks(tenantId, saved, template, 1, applicantId);
        taskRepository.saveAll(tasks);

        // 待办通知：每个审批人各 1 条
        for (ApprovalTask task : tasks) {
            notificationService.create(tenantId, task.getApproverId(), NotificationType.APPROVAL_TASK,
                    "APPROVAL", saved.getId(),
                    "待审批：" + saved.getTitle(),
                    "您有一条待审批（" + transition.getActionName() + "），资产：" + asset.getAssetName());
        }

        // 容忍审计字段缺失（ImmutableCollections 的 Map.of 拒绝 null value，真实数据缺字段时会 NPE）
        Map<String, String> auditDetail = new HashMap<>();
        auditDetail.put("actionCode", transition.getActionCode());
        auditDetail.put("fromState", transition.getFromState());
        auditDetail.put("instanceId", saved.getId().toString());
        auditLogService.log("LIFECYCLE_APPROVAL_REQUIRED", "ASSET", asset.getId().toString(), auditDetail);
        return saved;
    }

    private String buildTitle(Asset asset, LifecycleTransition transition) {
        return "资产「" + asset.getAssetName() + "」" + transition.getActionName() + "审批";
    }

    /** 构建某节点的全部审批任务（按 USER/ROLE 解析审批人）。 */
    private List<ApprovalTask> buildNodeTasks(UUID tenantId, ApprovalInstance instance,
                                              ApprovalTemplate template, int nodeOrder, UUID operatorId) {
        ApprovalNode node = nodeRepository
                .findByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(tenantId, template.getId(), nodeOrder)
                .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                        "审批模板缺少第 " + nodeOrder + " 个节点"));
        List<UUID> approverIds = resolveApprovers(tenantId, node);
        if (approverIds.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "审批节点未解析到任何审批人");
        }
        List<ApprovalTask> tasks = new ArrayList<>();
        for (UUID approverId : approverIds) {
            ApprovalTask task = new ApprovalTask();
            task.setTenantId(tenantId);
            task.setInstanceId(instance.getId());
            task.setNodeOrder(nodeOrder);
            task.setApproverId(approverId);
            task.setApproverType(node.getApproverType());
            task.setStatus(TaskStatus.PENDING);
            task.setCreatedBy(operatorId);
            task.setUpdatedBy(operatorId);
            tasks.add(task);
        }
        return tasks;
    }

    /** USER -> 单审批人；ROLE -> 该角色下 ACTIVE 租户成员各 1 个审批人。 */
    private List<UUID> resolveApprovers(UUID tenantId, ApprovalNode node) {
        List<UUID> approvers = new ArrayList<>();
        if (node.getApproverType() == ApproverType.USER) {
            if (node.getApproverUserId() != null) {
                approvers.add(node.getApproverUserId());
            }
        } else {
            List<TenantUser> members = tenantUserRepository
                    .findByTenantIdAndRoleIdAndStatusAndDeletedFalse(tenantId, node.getApproverRoleId(), "ACTIVE");
            for (TenantUser tu : members) {
                approvers.add(tu.getPlatformUserId());
            }
        }
        return approvers;
    }

    // =========================================================================
    // 查询
    // =========================================================================

    /** 我的待办：当前用户为审批人的任务（可选状态过滤；null=全部状态）。 */
    public List<ApprovalTaskResponse> getMyTodos(UUID tenantId, UUID userId, TaskStatus status) {
        List<ApprovalTask> tasks = (status == null)
                ? taskRepository.findByTenantIdAndApproverIdAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, userId)
                : taskRepository.findByTenantIdAndApproverIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, userId, status);
        return toTaskResponses(tenantId, userId, tasks);
    }

    /** 审批实例列表（租户隔离；可选状态过滤）。 */
    public List<ApprovalInstanceResponse> getInstances(UUID tenantId, InstanceStatus status) {
        List<ApprovalInstance> instances = (status == null)
                ? instanceRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId)
                : instanceRepository.findByTenantIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(tenantId, status);
        return instances.stream().map(this::toInstanceResponse).toList();
    }

    /** 审批实例详情（基础信息 + 任务历史）。 */
    public ApprovalInstanceResponse getInstance(UUID tenantId, UUID userId, UUID id) {
        ApprovalInstance instance = instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        List<ApprovalTask> tasks = taskRepository
                .findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(tenantId, id);
        return toInstanceResponse(instance, tasks, userId);
    }

    // =========================================================================
    // 通过 / 驳回
    // =========================================================================

    /**
     * 通过审批。
     * - 校验当前用户为当前节点 pending 任务的审批人；非审批人 -> BUSINESS_RULE_VIOLATION。
     * - 同节点其余 pending 任务取消（首批生效）。
     * - 末节点 -> 实例 approved + 回调 LifecycleActionExecutor 真正流转+写事件(关联 instance) + 通知申请人。
     * - 非末节点 -> currentNodeOrder++，建下一节点任务 + 通知下一审批人。
     */
    @Transactional
    public ApprovalInstanceResponse approve(UUID tenantId, UUID userId, String displayName,
                                            UUID id, String comment) {
        ApprovalInstance instance = instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        if (instance.getStatus() != InstanceStatus.PENDING) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "审批实例状态不正确，无法审批");
        }
        int currentNode = instance.getCurrentNodeOrder();
        List<ApprovalTask> nodeTasks = taskRepository
                .findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, id, currentNode);

        ApprovalTask myTask = nodeTasks.stream()
                .filter(t -> t.getApproverId().equals(userId) && t.getStatus() == TaskStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                        "您不是该审批任务的审批人"));

        // 首批生效：同节点其余 pending 任务取消
        for (ApprovalTask t : nodeTasks) {
            if (!t.getId().equals(myTask.getId()) && t.getStatus() == TaskStatus.PENDING) {
                t.setStatus(TaskStatus.CANCELLED);
                t.setDecidedBy(userId);
                t.setDecidedAt(OffsetDateTime.now());
                taskRepository.save(t);
            }
        }
        myTask.setStatus(TaskStatus.APPROVED);
        myTask.setComment(comment);
        myTask.setDecidedBy(userId);
        myTask.setDecidedAt(OffsetDateTime.now());
        taskRepository.save(myTask);

        boolean isLastNode = !nodeRepository
                .existsByTenantIdAndTemplateIdAndNodeOrderAndDeletedFalse(tenantId, instance.getTemplateId(), currentNode + 1);

        if (isLastNode) {
            instance.setStatus(InstanceStatus.APPROVED);
            LifecycleTransition transition = transitionRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, instance.getTransitionId())
                    .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                            "关联的生命周期流转不存在"));
            // 回调生命周期真正流转 + 写事件（回填 approvalInstanceId）
            lifecycleActionExecutor.execute(tenantId, userId, displayName, instance.getAssetId(), transition,
                    instance.getReason(), new LinkedHashMap<>(), new ArrayList<>(), instance.getId());
            // 通知申请人
            notificationService.create(tenantId, instance.getApplicantId(), NotificationType.APPROVAL_APPROVED,
                    "APPROVAL", instance.getId(),
                    "审批通过：" + instance.getTitle(),
                    "您的「" + transition.getActionName() + "」审批已通过，资产状态已更新为 " + transition.getToState());
        } else {
            instance.setCurrentNodeOrder(currentNode + 1);
            ApprovalTemplate template = templateRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, instance.getTemplateId())
                    .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "审批模板不存在"));
            List<ApprovalTask> nextTasks = buildNodeTasks(tenantId, instance, template, currentNode + 1, userId);
            taskRepository.saveAll(nextTasks);
            for (ApprovalTask t : nextTasks) {
                notificationService.create(tenantId, t.getApproverId(), NotificationType.APPROVAL_FORWARDED,
                        "APPROVAL", instance.getId(),
                        "待审批：" + instance.getTitle(),
                        "审批已流转至下一节点，请您审批（" + instance.getActionCode() + "）");
            }
        }

        instance.setUpdatedBy(userId);
        ApprovalInstance saved = instanceRepository.save(instance);
        auditLogService.log("APPROVAL_DECISION", "APPROVAL", instance.getId().toString(),
                Map.of("decision", "APPROVED", "node", String.valueOf(currentNode)));

        List<ApprovalTask> tasks = taskRepository
                .findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(tenantId, id);
        return toInstanceResponse(saved, tasks, userId);
    }

    /**
     * 驳回审批。实例/任务=rejected；资产状态不变、不写事件；通知申请人（含驳回意见）。
     * 校验当前用户为当前节点 pending 任务的审批人；驳回意见必填。
     */
    @Transactional
    public ApprovalInstanceResponse reject(UUID tenantId, UUID userId, String displayName,
                                           UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "驳回必须填写意见");
        }
        ApprovalInstance instance = instanceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        if (instance.getStatus() != InstanceStatus.PENDING) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "审批实例状态不正确，无法审批");
        }
        int currentNode = instance.getCurrentNodeOrder();
        List<ApprovalTask> nodeTasks = taskRepository
                .findByTenantIdAndInstanceIdAndNodeOrderAndDeletedFalse(tenantId, id, currentNode);

        ApprovalTask myTask = nodeTasks.stream()
                .filter(t -> t.getApproverId().equals(userId) && t.getStatus() == TaskStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                        "您不是该审批任务的审批人"));

        // 同节点其余 pending 任务一并置为 rejected（被当前审批人驳回）
        for (ApprovalTask t : nodeTasks) {
            if (t.getStatus() == TaskStatus.PENDING) {
                t.setStatus(TaskStatus.REJECTED);
                t.setComment(t.getId().equals(myTask.getId()) ? comment : "其他审批人已驳回");
                t.setDecidedBy(userId);
                t.setDecidedAt(OffsetDateTime.now());
                taskRepository.save(t);
            }
        }

        instance.setStatus(InstanceStatus.REJECTED);
        instance.setUpdatedBy(userId);
        ApprovalInstance saved = instanceRepository.save(instance);

        notificationService.create(tenantId, instance.getApplicantId(), NotificationType.APPROVAL_REJECTED,
                "APPROVAL", instance.getId(),
                "审批驳回：" + instance.getTitle(),
                "您的「" + instance.getActionCode() + "」审批被驳回：" + comment);

        auditLogService.log("APPROVAL_DECISION", "APPROVAL", instance.getId().toString(),
                Map.of("decision", "REJECTED", "node", String.valueOf(currentNode)));

        List<ApprovalTask> tasks = taskRepository
                .findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(tenantId, id);
        return toInstanceResponse(saved, tasks, userId);
    }

    // =========================================================================
    // 响应映射
    // =========================================================================

    private List<ApprovalTaskResponse> toTaskResponses(UUID tenantId, UUID userId, List<ApprovalTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<UUID> instanceIds = tasks.stream().map(ApprovalTask::getInstanceId).distinct().toList();
        List<ApprovalInstance> instances = instanceRepository.findByTenantIdAndIdInAndDeletedFalse(tenantId, instanceIds);
        Map<UUID, ApprovalInstance> instanceMap = instances.stream()
                .collect(Collectors.toMap(ApprovalInstance::getId, i -> i, (a, b) -> a));
        Set<UUID> approverIds = tasks.stream().map(ApprovalTask::getApproverId).collect(Collectors.toSet());
        Map<UUID, String> names = loadApproverNames(approverIds);

        List<ApprovalTaskResponse> result = new ArrayList<>();
        for (ApprovalTask t : tasks) {
            ApprovalInstance inst = instanceMap.get(t.getInstanceId());
            result.add(toTaskResponse(t, inst, names.get(t.getApproverId()), userId));
        }
        return result;
    }

    private ApprovalInstanceResponse toInstanceResponse(ApprovalInstance instance) {
        List<ApprovalTask> tasks = taskRepository
                .findByTenantIdAndInstanceIdAndDeletedFalseOrderByNodeOrderAscCreatedAtAsc(
                        instance.getTenantId(), instance.getId());
        return toInstanceResponse(instance, tasks, null);
    }

    private ApprovalInstanceResponse toInstanceResponse(ApprovalInstance instance, List<ApprovalTask> tasks, UUID userId) {
        String actionName = instance.getActionCode();
        Optional<LifecycleTransition> transitionOpt = transitionRepository
                .findByTenantIdAndIdAndDeletedFalse(instance.getTenantId(), instance.getTransitionId());
        if (transitionOpt.isPresent()) {
            actionName = transitionOpt.get().getActionName();
        }
        Set<UUID> approverIds = tasks.stream().map(ApprovalTask::getApproverId).collect(Collectors.toSet());
        Map<UUID, String> names = loadApproverNames(approverIds);
        List<ApprovalTaskResponse> taskResponses = tasks.stream()
                .map(t -> toTaskResponse(t, null, names.get(t.getApproverId()), userId))
                .toList();
        return ApprovalInstanceResponse.builder()
                .id(instance.getId())
                .title(instance.getTitle())
                .assetId(instance.getAssetId())
                .actionCode(instance.getActionCode())
                .actionName(actionName)
                .fromState(instance.getFromState())
                .toState(instance.getToState())
                .applicantId(instance.getApplicantId())
                .applicantName(instance.getApplicantName())
                .reason(instance.getReason())
                .status(instance.getStatus())
                .currentNodeOrder(instance.getCurrentNodeOrder())
                .createdAt(instance.getCreatedAt())
                .tasks(taskResponses)
                .build();
    }

    private ApprovalTaskResponse toTaskResponse(ApprovalTask t, ApprovalInstance inst, String approverName, UUID userId) {
        boolean canDecide = t.getStatus() == TaskStatus.PENDING
                && (userId == null || t.getApproverId().equals(userId))
                && (inst == null || inst.getStatus() == InstanceStatus.PENDING);
        ApprovalInstanceSummary summary = (inst == null) ? null : ApprovalInstanceSummary.builder()
                .id(inst.getId())
                .title(inst.getTitle())
                .assetId(inst.getAssetId())
                .actionCode(inst.getActionCode())
                .actionName(inst.getActionName())
                .fromState(inst.getFromState())
                .toState(inst.getToState())
                .applicantId(inst.getApplicantId())
                .applicantName(inst.getApplicantName())
                .reason(inst.getReason())
                .status(inst.getStatus())
                .build();
        return ApprovalTaskResponse.builder()
                .id(t.getId())
                .instanceId(t.getInstanceId())
                .nodeOrder(t.getNodeOrder())
                .approverId(t.getApproverId())
                .approverName(approverName)
                .approverType(t.getApproverType())
                .status(t.getStatus())
                .comment(t.getComment())
                .decidedAt(t.getDecidedAt())
                .createdAt(t.getCreatedAt())
                .canDecide(canDecide)
                .instance(summary)
                .build();
    }

    private Map<UUID, String> loadApproverNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> map = new HashMap<>();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            Optional<PlatformUser> u = platformUserRepository.findById(id);
            u.ifPresent(platformUser -> map.put(id, platformUser.getDisplayName()));
        }
        return map;
    }
}
