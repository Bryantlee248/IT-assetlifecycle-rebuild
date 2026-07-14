package com.itam.tenantadmin;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.security.JwtUserPrincipal;
import com.itam.security.UserType;
import com.itam.tenantadmin.dto.CreateOrgRequest;
import com.itam.tenantadmin.dto.OrgNode;
import com.itam.tenantadmin.dto.UpdateOrgRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 组织父节点跨租户校验（纯单元，Mock OrganizationRepository）。
 * A 租户用户以 B 租户组织作为 parentId 创建/更新 -> 抛 NOT_FOUND（"父组织不存在或不属于当前租户"）。
 */
@ExtendWith(MockitoExtension.class)
class OrganizationCrossTenantTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AuditLogService auditLogService;

    private OrganizationService organizationService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationRepository, auditLogService);
        JwtUserPrincipal principal = new JwtUserPrincipal(UUID.randomUUID(), "u", "U", UserType.TENANT, tenantA,
                UUID.randomUUID(), Set.of("org:create", "org:update"), false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, Collections.singletonList(new SimpleGrantedAuthority("org:create"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_with_same_tenant_parent_succeeds() {
        Organization parent = new Organization();
        parent.setId(orgA);
        when(organizationRepository.existsByTenantIdAndCode(tenantA, "code-a")).thenReturn(false);
        when(organizationRepository.findByTenantIdAndId(tenantA, orgA)).thenReturn(Optional.of(parent));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

        OrgNode node = organizationService.create(new CreateOrgRequest(orgA, "name", "code-a", "DEPT", 0));
        assertThat(node).isNotNull();
    }

    @Test
    void create_with_cross_tenant_parent_fails() {
        when(organizationRepository.existsByTenantIdAndCode(tenantA, "code-a")).thenReturn(false);
        // orgB 属于 tenantB，对 tenantA 不可见
        when(organizationRepository.findByTenantIdAndId(tenantA, orgB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.create(new CreateOrgRequest(orgB, "name", "code-a", "DEPT", 0)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.NOT_FOUND);
    }

    @Test
    void update_with_cross_tenant_parent_fails() {
        Organization existing = new Organization();
        existing.setId(orgA);
        when(organizationRepository.findByTenantIdAndId(tenantA, orgA)).thenReturn(Optional.of(existing));
        when(organizationRepository.findByTenantIdAndId(tenantA, orgB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.update(orgA,
                new UpdateOrgRequest(orgB, "newName", "newCode", "DEPT", 0, "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.NOT_FOUND);
    }
}
