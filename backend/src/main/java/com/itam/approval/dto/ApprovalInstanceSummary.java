package com.itam.approval.dto;

import com.itam.approval.entity.InstanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 审批任务所关联的审批实例精简摘要（供待办列表展示业务上下文）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalInstanceSummary {

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
}
