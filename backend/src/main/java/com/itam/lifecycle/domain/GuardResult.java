package com.itam.lifecycle.domain;

import java.util.List;

/**
 * 守卫评估结果。无违规时 passed=true、errors 为空；
 * 有违规时由 LifecycleGuardEvaluator 直接抛出 BusinessException（聚合消息）。
 */
public record GuardResult(boolean passed, List<String> errors) {

    public static GuardResult ok() {
        return new GuardResult(true, List.of());
    }
}
