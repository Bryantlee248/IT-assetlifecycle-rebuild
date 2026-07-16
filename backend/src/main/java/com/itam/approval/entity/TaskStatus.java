package com.itam.approval.entity;

/**
 * 审批任务状态。pending=待审；approved=已通过；rejected=已驳回；
 * cancelled=已取消（同一节点首批生效后，其余同节点 pending 任务取消）。
 */
public enum TaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
