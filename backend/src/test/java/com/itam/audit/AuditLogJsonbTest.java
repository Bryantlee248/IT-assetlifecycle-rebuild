package com.itam.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 审计日志 JSONB 映射单元测试（无 Spring 上下文、不依赖 PG/Redis）。
 * 1) AuditLog.detail 字段为 Map 且带 @JdbcTypeCode(SqlTypes.JSON) 注解（方案 A）。
 * 2) AuditLogService.log 以结构化 Map 持久化 detail。
 * 3) 审计写入失败时吞掉异常，不影响主业务。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogJsonbTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void detail_field_is_mapped_as_jsonb_map() throws Exception {
        Field field = AuditLog.class.getDeclaredField("detail");
        assertThat(field.getType()).isEqualTo(Map.class);
        assertThat(field.isAnnotationPresent(JdbcTypeCode.class)).isTrue();
        assertThat(field.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void log_persists_structured_map_detail() {
        AuditLogService service = new AuditLogService(auditLogRepository, objectMapper);
        Map<String, Object> detail = Map.of("code", "demo", "count", 3);
        service.log("ORG_CREATE", "ORGANIZATION", "biz", detail);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail())
                .containsEntry("code", "demo")
                .containsEntry("count", 3);
    }

    @Test
    void log_with_null_detail_does_not_fail() {
        AuditLogService service = new AuditLogService(auditLogRepository, objectMapper);
        service.log("ORG_CREATE", "ORGANIZATION", "biz", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void log_swallows_repository_failure() {
        AuditLogService service = new AuditLogService(auditLogRepository, objectMapper);
        doThrow(new RuntimeException("db down")).when(auditLogRepository).save(any());
        // 必须不向上抛出，主业务流程可继续
        service.log("ORG_CREATE", "ORGANIZATION", "biz", Map.of("k", "v"));
    }
}
