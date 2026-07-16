package com.itam.approval.dto;

import com.itam.approval.entity.ApproverType;
import com.itam.approval.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审批任务响应（含实例上下文）。canDecide 表示当前用户是否为该 pending 任务审批人。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTaskResponse {

    private UUID id;
    private UUID instanceId;
    private int nodeOrder;
    private UUID approverId;
    private String approverName;
    private ApproverType approverType;
    private TaskStatus status;
    private String comment;
    private OffsetDateTime decidedAt;
    private Instant createdAt;
    private boolean canDecide;
    private ApprovalInstanceSummary instance;
}
