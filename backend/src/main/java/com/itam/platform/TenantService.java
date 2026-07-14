package com.itam.platform;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.PageResult;
import com.itam.common.result.ResultCode;
import com.itam.platform.dto.CreateTenantRequest;
import com.itam.platform.dto.TenantResponse;
import com.itam.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 平台租户管理：仅平台管理员可调用。创建/列表/启停。
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public TenantResponse create(CreateTenantRequest req) {
        if (tenantRepository.existsByCode(req.code())) {
            throw new BusinessException(ResultCode.CONFLICT, "租户编码已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setName(req.name());
        tenant.setCode(req.code());
        tenant.setDescription(req.description());
        tenant.setStatus("ACTIVE");
        UUID operator = TenantContext.getCurrentUserId();
        tenant.setCreatedBy(operator);
        tenant.setUpdatedBy(operator);
        tenant = tenantRepository.save(tenant);
        auditLogService.log("TENANT_CREATE", "TENANT", tenant.getId().toString(),
                Map.of("code", req.code(), "name", req.name()));
        return toResponse(tenant);
    }

    public PageResult<TenantResponse> list(int page, int size, String keyword) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Tenant> result = (keyword == null || keyword.isBlank())
                ? tenantRepository.findAll(pageable)
                : tenantRepository.findByNameContainingIgnoreCase(keyword, pageable);
        return PageResult.of(page, size, result.getTotalElements(),
                result.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional
    public void disable(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租户不存在"));
        tenant.setStatus("DISABLED");
        tenant.setUpdatedBy(TenantContext.getCurrentUserId());
        auditLogService.log("TENANT_DISABLE", "TENANT", id.toString(), null);
    }

    @Transactional
    public void enable(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租户不存在"));
        tenant.setStatus("ACTIVE");
        tenant.setUpdatedBy(TenantContext.getCurrentUserId());
        auditLogService.log("TENANT_ENABLE", "TENANT", id.toString(), null);
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getCode(), t.getStatus(),
                t.getDescription(), t.getCreatedAt());
    }
}
