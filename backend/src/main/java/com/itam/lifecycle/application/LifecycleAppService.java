package com.itam.lifecycle.application;

import com.itam.approval.application.ApprovalService;
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
import com.itam.lifecycle.dto.LifecycleStatusResponse;
import com.itam.lifecycle.entity.LifecycleEvent;
import com.itam.lifecycle.entity.LifecycleState;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.AssetLifecycleRepository;
import com.itam.lifecycle.repository.LifecycleEventRepository;
import com.itam.lifecycle.repository.LifecycleStateRepository;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import com.itam.metadata.application.StatePermissionService;
import com.itam.tenantadmin.Role;
import com.itam.tenantadmin.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 生命周期应用服务：状态/事件/动作查询 + executeAction 全链路。
 *
 * 全链路：资产存在性 -> 模板解析 -> 状态/动作校验 -> 守卫 -> 状态权限二次拦截 ->
 *       审批判定 -> 条件更新（直转）/ 创建审批实例（需审批）-> 事件 -> 审计。
 * 不修改 Asset 实体；lifecycle_status 仅经 LifecycleActionExecutor（直转或审批回调）变更。
 */
@Service
@RequiredArgsConstructor
public class LifecycleAppService {

    private final AssetLifecycleRepository assetLifecycleRepository;
    private final LifecycleStateRepository lifecycleStateRepository;
    private final LifecycleTransitionRepository lifecycleTransitionRepository;
    private final LifecycleEventRepository lifecycleEventRepository;
    private final TemplateResolver templateResolver;
    private final LifecycleGuardEvaluator guardEvaluator;
    private final AuditLogService auditLogService;
    private final RoleRepository roleRepository;
    private final ApprovalTemplateRepository approvalTemplateRepository;
    private final StatePermissionService statePermissionService;
    private final ApprovalService approvalService;
    private final LifecycleActionExecutor lifecycleActionExecutor;

    /** E1：当前状态 + 模板信息。 */
    public LifecycleStatusResponse getStatus(UUID tenantId, UUID assetId) {
        Asset asset = assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        LifecycleTemplate template = templateResolver.resolve(tenantId, asset);
        String currentState = asset.getLifecycleStatus();
        Optional<LifecycleState> state =
                lifecycleStateRepository
                        .findByTenantIdAndTemplateIdAndStateCodeAndDeletedFalse(tenantId, template.getId(), currentState);
        String currentStateName = state.map(LifecycleState::getStateName).orElse(currentState);
        return LifecycleStatusResponse.builder()
                .assetId(assetId)
                .templateId(template.getId())
                .templateName(template.getName())
                .currentState(currentState)
                .currentStateName(currentStateName)
                .assetKind(asset.getAssetKind())
                .build();
    }

    /** E3：当前状态下可执行动作列表（经状态权限过滤后返回）。 */
    public List<LifecycleActionResponse> getActions(UUID tenantId, Set<String> roleCodes, UUID assetId) {
        Asset asset = assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        LifecycleTemplate template = templateResolver.resolve(tenantId, asset);
        String currentState = asset.getLifecycleStatus();

        Map<String, String> stateNameMap = lifecycleStateRepository
                .findByTenantIdAndTemplateIdAndDeletedFalse(tenantId, template.getId()).stream()
                .collect(Collectors.toMap(LifecycleState::getStateCode, LifecycleState::getStateName,
                        (a, b) -> a));

        List<LifecycleActionResponse> all = lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndFromStateAndDeletedFalseOrderBySortOrderAsc(
                        tenantId, template.getId(), currentState).stream()
                .map(t -> LifecycleActionResponse.builder()
                        .actionCode(t.getActionCode())
                        .actionName(t.getActionName())
                        .toState(t.getToState())
                        .toStateName(stateNameMap.getOrDefault(t.getToState(), t.getToState()))
                        .requireApproval(t.isRequireApproval())
                        .requireAttachment(t.isRequireAttachment())
                        .guardRule(t.getGuardRule())
                        .build())
                .toList();

        // 状态权限过滤：当前角色不允许执行的动作不返回（前端隐藏）
        Set<UUID> roleIds = resolveRoleIds(tenantId, roleCodes);
        return all.stream()
                .filter(a -> statePermissionService.isActionAllowed(
                        tenantId, roleIds, asset.getAssetTypeId(), currentState, a.getActionCode()))
                .toList();
    }

    /** E2：生命周期事件时间线（倒序）。 */
    public List<LifecycleEventResponse> getEvents(UUID tenantId, UUID assetId) {
        assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        return lifecycleEventRepository.findByTenantIdAndAssetIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, assetId).stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    /** E4：执行生命周期动作（闭环核心）。 */
    @Transactional
    public LifecycleActionResult executeAction(UUID tenantId, UUID userId, String displayName,
                                               Set<String> roleCodes, UUID assetId,
                                               String actionCode, ExecuteActionRequest req) {
        // 1) 资产存在性（跨租户 -> 404）
        Asset asset = assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));

        // 2) 模板解析（无模板 -> 422）
        LifecycleTemplate template = templateResolver.resolve(tenantId, asset);

        // 3) 当前状态
        String currentState = asset.getLifecycleStatus();

        // 4) 定位流转（非法动作 -> 422）
        LifecycleTransition transition = lifecycleTransitionRepository
                .findByTenantIdAndTemplateIdAndActionCodeAndFromStateAndDeletedFalse(
                        tenantId, template.getId(), actionCode, currentState)
                .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                        "非法生命周期动作：当前状态 " + currentState + " 不允许 " + actionCode));

        // 5) 守卫评估（缺字段/缺附件 -> 422）
        guardEvaluator.evaluate(asset, transition.getGuardRule(), req);

        // 6) 状态权限二次拦截（命中规则且动作不在 allowed_actions 并集 -> 422 + 审计）
        Set<UUID> roleIds = resolveRoleIds(tenantId, roleCodes);
        if (!statePermissionService.isActionAllowed(tenantId, roleIds, asset.getAssetTypeId(), currentState, actionCode)) {
            auditLogService.log("STATE_PERMISSION_DENIED", "ASSET", assetId.toString(),
                    Map.of("actionCode", actionCode, "state", currentState));
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                    "当前角色无权限执行该生命周期动作：" + actionCode);
        }

        // 7) 需审批分支：创建审批实例（不改进状态、不写事件），返回 approval_required
        if (transition.isRequireApproval()) {
            ApprovalTemplate approvalTemplate = approvalTemplateRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, transition.getApprovalTemplateId())
                    .orElseThrow(() -> new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "未配置审批模板"));
            com.itam.approval.entity.ApprovalInstance instance = approvalService.createInstance(
                    tenantId, asset, transition, approvalTemplate, userId, displayName, req.reason());
            auditLogService.log("LIFECYCLE_APPROVAL_REQUIRED", "ASSET", assetId.toString(),
                    Map.of("actionCode", actionCode, "fromState", currentState,
                            "instanceId", instance.getId().toString()));
            return new LifecycleActionResult("approval_required", null, null, instance.getId(), null);
        }

        // 8) 直转路径：条件更新 + 写事件（经 LifecycleActionExecutor，审批实例 ID 为 null）
        return lifecycleActionExecutor.execute(tenantId, userId, displayName, assetId, transition,
                req.reason(), req.formData(), req.attachmentIds(), null);
    }

    /** 将 principal 的 role 码解析为 roleId 集合（状态权限用）。 */
    private Set<UUID> resolveRoleIds(UUID tenantId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String code : roleCodes) {
            roleRepository.findByTenantIdAndCode(tenantId, code).ifPresent(r -> ids.add(r.getId()));
        }
        return ids;
    }

    private LifecycleEventResponse toEventResponse(LifecycleEvent e) {
        return LifecycleEventResponse.builder()
                .id(e.getId())
                .actionCode(e.getActionCode())
                .actionName(e.getActionName())
                .fromState(e.getFromState())
                .toState(e.getToState())
                .operatorId(e.getOperatorId())
                .operatorName(e.getOperatorName())
                .reason(e.getReason())
                .formData(e.getFormData())
                .attachmentIds(e.getAttachmentIds())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
