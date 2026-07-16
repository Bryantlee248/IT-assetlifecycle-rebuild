package com.itam.notification.entity;

import com.itam.common.base.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 站内通知（租户级）。双隔离：tenant_id + receiver_id。
 * read_at 非空即已读；markRead/markAllRead 服务端强校验 receiver_id 防越权。
 */
@Entity
@Table(name = "notifications")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends TenantEntity {

    @Column(name = "receiver_id", nullable = false, columnDefinition = "uuid")
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "business_type", length = 64)
    private String businessType;

    @Column(name = "business_id", columnDefinition = "uuid")
    private UUID businessId;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
