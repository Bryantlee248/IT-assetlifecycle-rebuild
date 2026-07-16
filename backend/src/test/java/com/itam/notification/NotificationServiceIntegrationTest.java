package com.itam.notification;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.notification.application.NotificationService;
import com.itam.notification.entity.Notification;
import com.itam.notification.entity.NotificationType;
import com.itam.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通知服务集成测试（Testcontainers + 真实 PostgreSQL + Flyway 迁移）。
 *
 * <p>默认跳过：仅当环境变量 {@code ITAM_INTEGRATION=true} 且本机有 Docker 时执行，
 * 以免无容器环境导致本地 {@code mvn test} 失败。用于真实落库验证团队主理人复核点①：
 *  ① 创建审批生成通知；② 未读数正确；③ 标记已读后未读减少；
 *  ④ 全部已读清零；⑤ 不能读别人通知（越权返回 ASSET_NOT_FOUND/40401）。
 *
 * <p>所有用例使用随机 tenant_id，避免与 V9 种子数据冲突。
 */
@DataJpaTest
@Testcontainers
@Import(NotificationService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "ITAM_INTEGRATION", matches = "true")
class NotificationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.driver-class-name", PG::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @MockBean
    private AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    private NotificationService notificationService;
    @org.springframework.beans.factory.annotation.Autowired
    private NotificationRepository notificationRepository;

    private Notification fresh(UUID tenantId, UUID receiverId, String title) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setReceiverId(receiverId);
        n.setType(NotificationType.APPROVAL_TASK);
        n.setTitle(title);
        n.setContent("content");
        return n;
    }

    @Test
    void create_thenUnreadCount_thenMarkRead_decreasesCount() {
        UUID tenantId = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t1", "c1");
        notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t2", "c2");
        notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t3", "c3");

        // ② 未读数正确
        assertThat(notificationService.unreadCount(tenantId, receiver)).isEqualTo(3L);

        // ③ 标记已读后未读减少
        Notification first = notificationRepository
                .findByTenantIdAndReceiverIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, receiver).get(0);
        notificationService.markRead(tenantId, receiver, first.getId());
        assertThat(notificationService.unreadCount(tenantId, receiver)).isEqualTo(2L);
    }

    @Test
    void markRead_crossUser_throwsAssetNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        Notification n = notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t1", "c1");

        // ⑤ 不能读别人通知：越权标记返回 ASSET_NOT_FOUND(40401)
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> notificationService.markRead(tenantId, stranger, n.getId()));
        assertEquals(ResultCode.ASSET_NOT_FOUND, ex.getResultCode());
    }

    @Test
    void markAllRead_clearsUnreadCount() {
        UUID tenantId = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t1", "c1");
        notificationService.create(tenantId, receiver, NotificationType.APPROVAL_TASK,
                "APPROVAL", UUID.randomUUID(), "t2", "c2");

        assertThat(notificationService.unreadCount(tenantId, receiver)).isEqualTo(2L);
        // ④ 全部已读清零
        notificationService.markAllRead(tenantId, receiver);
        assertThat(notificationService.unreadCount(tenantId, receiver)).isEqualTo(0L);
    }
}
