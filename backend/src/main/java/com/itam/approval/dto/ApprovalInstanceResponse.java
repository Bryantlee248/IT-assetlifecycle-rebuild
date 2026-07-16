package com.itam.approval.dto;

import com.itam.approval.entity.InstanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 审批实例响应（基础信息 + 业务上下文 + 任务历史）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalInstanceResponse {

    private UUID id;
    private String title;
    private UUID assetId;
    private String actionCode;
    private String actionName;
    private String fromState;
    private String toState;
    private UUID applicantId;
    private String applicantName;
    private String reason;
    private InstanceStatus status;
    private int currentNodeOrder;
    private Instant createdAt;
    private List<ApprovalTaskResponse> tasks;
}
