package com.itam.notification.application;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.notification.dto.NotificationResponse;
import com.itam.notification.entity.Notification;
import com.itam.notification.entity.NotificationType;
import com.itam.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单元测试（Mockito，无 Spring 上下文）。
 * 聚焦双隔离（tenant_id + receiver_id）：
 *  ① create 持久化租户与接收人；② list 仅返回 receiver 自己的；③ unreadCount 只数未读；
 *  ④ markRead 正常标记已读；⑤ 越权 markRead（他人通知）按 ASSET_NOT_FOUND(40401)。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private NotificationService service;

    private UUID tenantId;
    private UUID receiverId;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        businessId = UUID.randomUUID();
    }

    @Test
    void create_persistsTenantAndReceiver() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification saved = service.create(tenantId, receiverId, NotificationType.APPROVAL_TASK,
                "APPROVAL", businessId, "待审批：资产A采购审批", "您有一条待审批");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification captured = cap.getValue();
        assertEquals(tenantId, captured.getTenantId());
        assertEquals(receiverId, captured.getReceiverId());
        assertEquals(NotificationType.APPROVAL_TASK, captured.getType());
        assertEquals("APPROVAL", captured.getBusinessType());
        assertEquals(businessId, captured.getBusinessId());
        assertEquals("待审批：资产A采购审批", captured.getTitle());
        assertEquals("您有一条待审批", captured.getContent());
        assertEquals(saved, captured);
    }

    @Test
    void list_returnsOnlyReceiverOwnNotifications() {
        Notification n1 = new Notification();
        n1.setId(UUID.randomUUID());
        n1.setReceiverId(receiverId);
        n1.setTitle("n1");
        Notification n2 = new Notification();
        n2.setId(UUID.randomUUID());
        n2.setReceiverId(receiverId);
        n2.setTitle("n2");
        when(notificationRepository.findByTenantIdAndReceiverIdAndDeletedFalseOrderByCreatedAtDesc(
                tenantId, receiverId)).thenReturn(List.of(n1, n2));

        List<NotificationResponse> result = service.list(tenantId, receiverId, null);

        // 服务端仅按 (tenant_id, receiver_id) 查询，返回列表严格等于 receiver 自己的通知。
        assertEquals(2, result.size());
        assertEquals("n1", result.get(0).getTitle());
        assertEquals("n2", result.get(1).getTitle());
        // 隔离契约：查询必须使用 receiverId 入参，而非其他用户。
        verify(notificationRepository).findByTenantIdAndReceiverIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(tenantId), eq(receiverId));
    }

    @Test
    void unreadCount_countsOnlyUnread() {
        when(notificationRepository.countByTenantIdAndReceiverIdAndReadAtIsNullAndDeletedFalse(
                tenantId, receiverId)).thenReturn(3L);

        long count = service.unreadCount(tenantId, receiverId);

        assertEquals(3L, count);
        verify(notificationRepository).countByTenantIdAndReceiverIdAndReadAtIsNullAndDeletedFalse(
                eq(tenantId), eq(receiverId));
    }

    @Test
    void markRead_successMarksRead() {
        when(notificationRepository.markRead(tenantId, receiverId, businessId)).thenReturn(1);

        service.markRead(tenantId, receiverId, businessId);

        verify(notificationRepository).markRead(eq(tenantId), eq(receiverId), eq(businessId));
    }

    @Test
    void markRead_crossUser_throwsAssetNotFound() {
        // 模拟"他人通知"：服务端强校验 receiver_id，影响行数 0 -> ASSET_NOT_FOUND(40401)。
        UUID strangerId = UUID.randomUUID();
        when(notificationRepository.markRead(any(), any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead(tenantId, strangerId, businessId));

        assertEquals(ResultCode.ASSET_NOT_FOUND, ex.getResultCode());
        verify(notificationRepository, never()).save(any());
    }
}
