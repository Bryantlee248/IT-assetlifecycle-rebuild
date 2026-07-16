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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生命周期动作纯执行器（从 LifecycleAppService.executeAction 抽取）。
 *
 * 承载「在已校验前提下完成状态流转并写事件」的单一职责，供两类调用方复用：
 *   - 直转路径：LifecycleAppService.executeAction 无需审批时调用（approvalInstanceId=null）。
 *   - 审批通过路径：ApprovalService.approve(末节点) 回调，回填 approvalInstanceId。
 *
 * 红线：lifecycle_status 只经本执行器（updateStatusIfUnchanged 条件更新）变更，保证单一改点。
 */
@Service
@RequiredArgsConstructor
public class LifecycleActionExecutor {

    private final AssetLifecycleRepository assetLifecycleRepository;
    private final LifecycleEventRepository lifecycleEventRepository;
    private final AuditLogService auditLogService;

    /**
     * 条件更新状态 + 写生命周期事件（可关联审批实例）。
     *
     * @param approvalInstanceId 审批通过回调时回填实例 ID；直转路径传 null。
     * @return 流转结果（result=transitioned）。
     */
    @Transactional
    public LifecycleActionResult execute(UUID tenantId, UUID operatorId, String operatorName,
                                         UUID assetId, LifecycleTransition transition,
                                         String reason, Map<String, Object> formData,
                                         List<UUID> attachmentIds, UUID approvalInstanceId) {
        String fromState = transition.getFromState();
        String toState = transition.getToState();

        // 并发保护：仅当前状态仍为 fromState 时才写入（影响行数 0 -> CONFLICT）
        int affected = assetLifecycleRepository.updateStatusIfUnchanged(
                assetId, tenantId, fromState, toState, operatorId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "生命周期状态已被其他操作变更，请刷新后重试");
        }

        LifecycleEvent event = new LifecycleEvent();
        event.setTenantId(tenantId);
        event.setAssetId(assetId);
        event.setTemplateId(transition.getTemplateId());
        event.setTransitionId(transition.getId());
        event.setActionCode(transition.getActionCode());
        event.setActionName(transition.getActionName());
        event.setFromState(fromState);
        event.setToState(toState);
        event.setOperatorId(operatorId);
        event.setOperatorName(operatorName);
        event.setReason(reason);
        event.setFormData(formData != null ? formData : new LinkedHashMap<>());
        event.setAttachmentIds(attachmentIds != null ? attachmentIds : new ArrayList<>());
        event.setApprovalInstanceId(approvalInstanceId);
        event.setCreatedBy(operatorId);
        event.setUpdatedBy(operatorId);
        LifecycleEvent saved = lifecycleEventRepository.save(event);

        auditLogService.log("LIFECYCLE_TRANSITION", "ASSET", assetId.toString(),
                Map.of("actionCode", transition.getActionCode(),
                        "fromState", fromState,
                        "toState", toState,
                        "approvalInstanceId", String.valueOf(approvalInstanceId)));

        return new LifecycleActionResult("transitioned", fromState, toState, approvalInstanceId, saved.getId());
    }
}
