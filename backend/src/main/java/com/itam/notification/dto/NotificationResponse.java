package com.itam.notification.dto;

import com.itam.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 通知响应 DTO。readAt 非空即已读。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private String businessType;
    private UUID businessId;
    private String title;
    private String content;
    private OffsetDateTime readAt;
    private Instant createdAt;
}
