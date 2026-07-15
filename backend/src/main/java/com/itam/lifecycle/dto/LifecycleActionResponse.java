package com.itam.lifecycle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * E3 响应元素：当前状态下可执行动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleActionResponse {

    private String actionCode;
    private String actionName;
    private String toState;
    private String toStateName;
    private boolean requireApproval;
    private boolean requireAttachment;
    private Map<String, Object> guardRule;
}
