package com.itam.lifecycle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * E1 响应：当前生命周期状态 + 模板信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleStatusResponse {

    private UUID assetId;
    private UUID templateId;
    private String templateName;
    private String currentState;
    private String currentStateName;
    private String assetKind;
}
