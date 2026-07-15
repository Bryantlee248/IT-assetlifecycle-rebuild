package com.itam.lifecycle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E2 响应元素：生命周期事件时间线。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleEventResponse {

    private UUID id;
    private String actionCode;
    private String actionName;
    private String fromState;
    private String toState;
    private UUID operatorId;
    private String operatorName;
    private String reason;
    private Map<String, Object> formData;
    private List<UUID> attachmentIds;
    private Instant createdAt;
}
