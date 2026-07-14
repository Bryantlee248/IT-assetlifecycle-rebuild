package com.itam.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 审计日志（平台级，可记录平台与租户操作）。不可变，无 updated/deleted。
 * tenant_id 为 null 表示平台级操作。
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false)
    private String actorType; // PLATFORM / TENANT

    @Column(nullable = false)
    private String action;

    @Column(name = "biz_type")
    private String bizType;

    @Column(name = "biz_id")
    private String bizId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    private String ip;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
