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
import com.itam.lifecycle.dto.LifecycleStatusResponse;
import com.itam.lifecycle.entity.LifecycleEvent;
import com.itam.lifecycle.entity.LifecycleState;
import com.itam.lifecycle.entity.LifecycleTemplate;
import com.itam.lifecycle.entity.LifecycleTransition;
import com.itam.lifecycle.repository.AssetLifecycleRepository;
import com.itam.lifecycle.repository.LifecycleEventRepository;
import com.itam.lifecycle.repository.LifecycleStateRepository;
import com.itam.lifecycle.repository.LifecycleTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 生命周期应用服务：状态/事件/动作查询 + executeAction 全链路。
 *
 * 全链路：资产存在性 -> 模板解析 -> 状态/动作校验 -> 守卫 -> 审批判定 ->
 *       条件更新（并发保护）-> 事件 -> 审计。
 * 不修改 Asset 实体；lifecycle_status 仅经 updateStatusIfUnchanged 条件更新变更。
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

    /** E3：当前状态下可执行动作列表。 */
    public List<LifecycleActionResponse> getActions(UUID tenantId, UUID assetId) {
        Asset asset = assetLifecycleRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        LifecycleTemplate template = templateResolver.resolve(tenantId, asset);
        String currentState = asset.getLifecycleStatus();

        Map<String, String> stateNameMap = lifecycleStateRepository
                .findByTenantIdAndTemplateIdAndDeletedFalse(tenantId, template.getId()).stream()
                .collect(Collectors.toMap(LifecycleState::getStateCode, LifecycleState::getStateName,
                        (a, b) -> a));

        return lifecycleTransitionRepository
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
                .collect(Collectors.toList());
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
                                               UUID assetId, String actionCode, ExecuteActionRequest req) {
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

        // 6) 审批分支：仅审计，状态不变、不写事件
        if (transition.isRequireApproval()) {
            auditLogService.log("LIFECYCLE_APPROVAL_REQUIRED", "ASSET", assetId.toString(),
                    Map.of("actionCode", actionCode, "fromState", currentState));
            return new LifecycleActionResult("approval_required", null, null, null, null);
        }

        // 7) 条件更新（并发保护）
        int n = assetLifecycleRepository.updateStatusIfUnchanged(
                assetId, tenantId, currentState, transition.getToState(), userId);
        if (n == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "生命周期状态已被其他操作变更，请刷新后重试");
        }

        // 8) 写事件 + 审计
        LifecycleEvent event = new LifecycleEvent();
        event.setTenantId(tenantId);
        event.setAssetId(assetId);
        event.setTemplateId(template.getId());
        event.setTransitionId(transition.getId());
        event.setActionCode(transition.getActionCode());
        event.setActionName(transition.getActionName());
        event.setFromState(currentState);
        event.setToState(transition.getToState());
        event.setOperatorId(userId);
        event.setOperatorName(displayName);
        event.setReason(req.reason());
        event.setFormData(req.formData() != null ? req.formData() : new LinkedHashMap<>());
        event.setAttachmentIds(req.attachmentIds() != null ? req.attachmentIds() : new ArrayList<>());
        event.setApprovalInstanceId(null);
        event.setCreatedBy(userId);
        event.setUpdatedBy(userId);
        LifecycleEvent saved = lifecycleEventRepository.save(event);

        auditLogService.log("LIFECYCLE_TRANSITION", "ASSET", assetId.toString(),
                Map.of("actionCode", actionCode, "fromState", currentState, "toState", transition.getToState()));

        return new LifecycleActionResult("transitioned", currentState, transition.getToState(), null, saved.getId());
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
