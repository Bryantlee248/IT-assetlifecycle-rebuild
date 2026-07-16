package com.itam.notification.entity;

/**
 * 通知类型。APPROVAL_TASK=待办；APPROVAL_APPROVED=通过；
 * APPROVAL_REJECTED=驳回；APPROVAL_FORWARDED=流转到下一审批人。
 */
public enum NotificationType {
    APPROVAL_TASK,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,
    APPROVAL_FORWARDED
}
