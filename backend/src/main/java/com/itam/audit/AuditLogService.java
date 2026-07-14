package com.itam.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itam.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * 审计日志服务：在关键写操作处显式调用，写入 actor/tenant/action/detail/IP。
 * actor 取自当前登录上下文；匿名场景(如登录失败)由调用方传入 actorId。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final UUID ANONYMOUS = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String action, String bizType, String bizId, Object detail) {
        UUID actorId = TenantContext.getCurrentUserId();
        String actorType = TenantContext.isPlatform() ? "PLATFORM" : "TENANT";
        if (actorId == null) {
            actorId = ANONYMOUS;
            actorType = "ANONYMOUS";
        }
        doLog(actorId, actorType, TenantContext.getCurrentTenantId(), action, bizType, bizId, detail);
    }

    public void logAnon(String action, String bizType, String bizId, UUID actorId, Object detail) {
        doLog(actorId, "ANONYMOUS", null, action, bizType, bizId, detail);
    }

    private void doLog(UUID actorId, String actorType, UUID tenantId,
                       String action, String bizType, String bizId, Object detail) {
        try {
            AuditLog entity = new AuditLog();
            entity.setActorId(actorId);
            entity.setActorType(actorType);
            entity.setTenantId(tenantId);
            entity.setAction(action);
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setDetail(toDetailMap(detail));
            entity.setIp(currentIp());
            auditLogRepository.save(entity);
        } catch (Exception ex) {
            log.error("Audit log failed for action {}: {}", action, ex.getMessage());
        }
    }

    /**
     * 将 detail 转为结构化 Map 以便 JPA 以 JSONB 形式持久化。
     * null -> null；已经是 Map 则直接复用；其余对象通过 Jackson 转换为 Map。
     * 任何转换异常都会上抛至 doLog 的 catch 中仅记录日志，不影响主业务。
     */
    private Map<String, Object> toDetailMap(Object detail) {
        if (detail == null) {
            return null;
        }
        if (detail instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) detail;
            return map;
        }
        return objectMapper.convertValue(detail, new TypeReference<Map<String, Object>>() {
        });
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
