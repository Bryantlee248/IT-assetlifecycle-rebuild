package com.itam.lifecycle.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * E4 响应 data：两种业务结果（transitioned / approval_required）。
 * approval_required 时 fromState/toState/eventId/approvalInstanceId 全 null。
 */
@Getter
@AllArgsConstructor
public class LifecycleActionResult {

    /** "transitioned" | "approval_required" */
    private final String result;
    private final String fromState;
    private final String toState;
    private final UUID approvalInstanceId;
    private final UUID eventId;
}
