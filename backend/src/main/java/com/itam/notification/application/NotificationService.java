package com.itam.notification.application;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.notification.dto.NotificationResponse;
import com.itam.notification.entity.Notification;
import com.itam.notification.entity.NotificationType;
import com.itam.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 站内通知服务：创建 / 列表 / 未读数 / 标记已读。
 *
 * 双隔离：所有读/写强制 (tenant_id, receiver_id)；markRead 用条件更新强校验 receiver_id，
 * 越权自然"影响行数 0" -> ASSET_NOT_FOUND(40401)，杜绝跨用户读他人通知。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    /** 创建一条通知（审批创建/通过/驳回/流转时调用）。receiverId 为具体平台用户。 */
    @Transactional
    public Notification create(UUID tenantId, UUID receiverId, NotificationType type,
                               String businessType, UUID businessId, String title, String content) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setReceiverId(receiverId);
        n.setType(type);
        n.setBusinessType(businessType);
        n.setBusinessId(businessId);
        n.setTitle(title);
        n.setContent(content);
        n.setCreatedBy(receiverId);
        n.setUpdatedBy(receiverId);
        return notificationRepository.save(n);
    }

    /** 我的通知列表（按 receiver_id + tenant_id 隔离；可选类型过滤）。 */
    public List<NotificationResponse> list(UUID tenantId, UUID userId, NotificationType type) {
        List<Notification> list = (type == null)
                ? notificationRepository.findByTenantIdAndReceiverIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, userId)
                : notificationRepository.findByTenantIdAndReceiverIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, userId, type);
        return list.stream().map(this::toResponse).toList();
    }

    /** 未读数：receiver 下 read_at 为空的数量。 */
    public long unreadCount(UUID tenantId, UUID userId) {
        return notificationRepository.countByTenantIdAndReceiverIdAndReadAtIsNullAndDeletedFalse(tenantId, userId);
    }

    /** 标记单条已读：服务端强校验 receiver_id，越权按 ASSET_NOT_FOUND(40401)。 */
    @Transactional
    public void markRead(UUID tenantId, UUID userId, UUID id) {
        int affected = notificationRepository.markRead(tenantId, userId, id);
        if (affected == 0) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND, "通知不存在或无权操作");
        }
    }

    /** 全部已读：条件更新该 receiver 下所有未读通知。 */
    @Transactional
    public void markAllRead(UUID tenantId, UUID userId) {
        notificationRepository.markAllRead(tenantId, userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .businessType(n.getBusinessType())
                .businessId(n.getBusinessId())
                .title(n.getTitle())
                .content(n.getContent())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
