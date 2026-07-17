package com.itam.notification.repository;

import com.itam.notification.entity.Notification;
import com.itam.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 站内通知仓储。双隔离：所有查询/更新均带 (tenant_id, receiver_id)。
 * 读/已读接口服务端强校验 receiver_id：越权自然"查无此通知" -> ASSET_NOT_FOUND(40401)。
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** 我的通知列表（按创建时间倒序）。 */
    List<Notification> findByTenantIdAndReceiverIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID receiverId);

    /** 我的通知列表（按类型过滤，创建时间倒序）。 */
    List<Notification> findByTenantIdAndReceiverIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID receiverId, NotificationType type);

    /** 未读数：receiver_id 下 read_at 为空的数量。 */
    long countByTenantIdAndReceiverIdAndReadAtIsNullAndDeletedFalse(UUID tenantId, UUID receiverId);

    /** 单条通知（强校验 tenant_id + receiver_id，越权则 empty）。 */
    Optional<Notification> findByTenantIdAndReceiverIdAndIdAndDeletedFalse(
            UUID tenantId, UUID receiverId, UUID id);

    /** 标记单条已读：仅当 tenant_id + receiver_id + id 匹配且尚未已读时更新。 */
    @Modifying
    @Query("""
           UPDATE Notification n
           SET n.readAt = CURRENT_TIMESTAMP, n.updatedAt = CURRENT_TIMESTAMP
           WHERE n.tenantId = :tenantId
             AND n.receiverId = :receiverId
             AND n.id = :id
             AND n.readAt IS NULL
             AND n.deleted = false
           """)
    int markRead(@Param("tenantId") UUID tenantId,
                 @Param("receiverId") UUID receiverId,
                 @Param("id") UUID id);

    /** 全部已读：条件更新该 receiver 下所有未读通知。 */
    @Modifying
    @Query("""
           UPDATE Notification n
           SET n.readAt = CURRENT_TIMESTAMP, n.updatedAt = CURRENT_TIMESTAMP
           WHERE n.tenantId = :tenantId
             AND n.receiverId = :receiverId
             AND n.readAt IS NULL
             AND n.deleted = false
           """)
    int markAllRead(@Param("tenantId") UUID tenantId,
                    @Param("receiverId") UUID receiverId);
}
