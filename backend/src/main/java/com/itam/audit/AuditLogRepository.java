package com.itam.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdAndCreatedAtBetween(UUID tenantId, Instant start, Instant end, Pageable pageable);

    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);
}
