package com.itam.approval.entity;

/**
 * 审批实例状态。pending=审批中；approved=末节点通过（业务已流转）；
 * rejected=被驳回（资产不变）；cancelled=被取消（如多节点首批生效，其余 pending 任务取消）。
 */
public enum InstanceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
