package com.itam.lifecycle.domain;

import com.itam.asset.entity.Asset;
import com.itam.common.exception.BusinessException;
import com.itam.lifecycle.dto.ExecuteActionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 生命周期守卫评估器纯单元测试（无 Spring 上下文）。
 * 覆盖：缺物理列 / 缺属性 / 需附件无附件 / 全通过，及错误聚合为单条 422 消息。
 */
class LifecycleGuardEvaluatorTest {

    private final LifecycleGuardEvaluator evaluator = new LifecycleGuardEvaluator();

    private Asset baseAsset() {
        Asset asset = new Asset();
        asset.setAssetKind("tangible");
        asset.setAssetTypeId(UUID.randomUUID());
        asset.setLifecycleStatus("planned");
        return asset;
    }

    @Test
    void missingPhysicalField_throwsAggregated422() {
        Asset asset = baseAsset(); // location_id / responsible_user_id / serial_no 均未设置
        Map<String, Object> guard = Map.of("requireFields",
                List.of("location_id", "responsible_user_id", "serial_no"));
        ExecuteActionRequest req = new ExecuteActionRequest("reason", Map.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(asset, guard, req));
        assertEquals(42200, ex.getResultCode().getCode());
        assertEquals("守卫校验失败：缺少必填字段[location_id, responsible_user_id, serial_no]",
                ex.getMessage());
    }

    @Test
    void missingAttributeField_throws422() {
        Asset asset = baseAsset(); // attributes 默认空 -> license_key 缺失
        Map<String, Object> guard = Map.of("requireAttributeFields", List.of("license_key"));
        ExecuteActionRequest req = new ExecuteActionRequest("reason", Map.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(asset, guard, req));
        assertEquals("守卫校验失败：缺少必填属性[license_key]", ex.getMessage());
    }

    @Test
    void requireAttachmentWithoutAttachment_throws422() {
        Asset asset = baseAsset();
        Map<String, Object> guard = Map.of("requireAttachment", true);
        ExecuteActionRequest req = new ExecuteActionRequest("reason", Map.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(asset, guard, req));
        assertEquals("守卫校验失败：该动作需要附件，附件能力将在后续阶段提供", ex.getMessage());
    }

    @Test
    void allSatisfied_passes() {
        Asset asset = baseAsset();
        asset.setLocationId(UUID.randomUUID());
        asset.setResponsibleUserId(UUID.randomUUID());
        asset.setSerialNo("SN-001");
        asset.setAttributes(Map.of("license_key", "KEY-ABC"));
        Map<String, Object> guard = Map.of(
                "requireFields", List.of("location_id", "responsible_user_id", "serial_no"),
                "requireAttributeFields", List.of("license_key"));
        ExecuteActionRequest req = new ExecuteActionRequest("reason", Map.of(), List.of());

        assertDoesNotThrow(() -> evaluator.evaluate(asset, guard, req));
    }

    @Test
    void emptyGuard_passes() {
        Asset asset = baseAsset();
        ExecuteActionRequest req = new ExecuteActionRequest("reason", Map.of(), List.of());
        assertDoesNotThrow(() -> evaluator.evaluate(asset, Map.of(), req));
    }
}
