package com.itam.approval.entity;

/**
 * 审批人来源类型。USER=指定用户；ROLE=按角色扇出（fan-out）为多名成员各建 1 个任务。
 */
public enum ApproverType {
    USER,
    ROLE
}
