package com.itam.lifecycle.domain;

import com.itam.asset.entity.Asset;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.lifecycle.dto.ExecuteActionRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生命周期守卫评估器：纯函数、无副作用。
 *
 * 输入：Asset（读 getPhysicalValue / getAttributes）+ guardRule（JSONB Map）
 *       + ExecuteActionRequest（reason / formData / attachmentIds）。
 * 校验规则：
 *   - requireFields：物理列 snake_case 编码，asset.getPhysicalValue(code) != null 方通过
 *   - requireAttributeFields：动态属性 field_code，asset.getAttributes().get(code) != null 方通过
 *   - requireAttachment：true 且未提供附件 -> 失败
 * 处理：先收集全部缺失项（不早退），最后聚合为单条消息抛出
 *       BusinessException(BUSINESS_RULE_VIOLATION, ...)。
 */
public class LifecycleGuardEvaluator {

    public GuardResult evaluate(Asset asset, Map<String, Object> guardRule, ExecuteActionRequest req) {
        if (guardRule == null || guardRule.isEmpty()) {
            return GuardResult.ok();
        }

        List<String> missingFields = new ArrayList<>();
        List<String> missingAttrFields = new ArrayList<>();
        boolean missingAttachment = false;

        // requireFields（物理列）
        Object requireFieldsObj = guardRule.get("requireFields");
        if (requireFieldsObj instanceof Collection<?> fields) {
            for (Object f : fields) {
                String code = String.valueOf(f);
                if (asset.getPhysicalValue(code) == null) {
                    missingFields.add(code);
                }
            }
        }

        // requireAttributeFields（动态属性）
        Object requireAttrObj = guardRule.get("requireAttributeFields");
        if (requireAttrObj instanceof Collection<?> fields) {
            Map<String, Object> attributes = asset.getAttributes();
            for (Object f : fields) {
                String code = String.valueOf(f);
                if (attributes == null || attributes.get(code) == null) {
                    missingAttrFields.add(code);
                }
            }
        }

        // requireAttachment
        Object requireAttachmentObj = guardRule.get("requireAttachment");
        if (Boolean.TRUE.equals(toBoolean(requireAttachmentObj))) {
            List<UUID> attachmentIds = req.attachmentIds();
            if (attachmentIds == null || attachmentIds.isEmpty()) {
                missingAttachment = true;
            }
        }

        if (missingFields.isEmpty() && missingAttrFields.isEmpty() && !missingAttachment) {
            return GuardResult.ok();
        }

        throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION,
                buildMessage(missingFields, missingAttrFields, missingAttachment));
    }

    private static String buildMessage(List<String> missingFields,
                                        List<String> missingAttrFields,
                                        boolean missingAttachment) {
        List<String> parts = new ArrayList<>();
        if (!missingFields.isEmpty()) {
            parts.add("缺少必填字段[" + String.join(", ", missingFields) + "]");
        }
        if (!missingAttrFields.isEmpty()) {
            parts.add("缺少必填属性[" + String.join(", ", missingAttrFields) + "]");
        }
        if (missingAttachment) {
            parts.add("该动作需要附件，附件能力将在后续阶段提供");
        }
        return "守卫校验失败：" + String.join("；", parts);
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
