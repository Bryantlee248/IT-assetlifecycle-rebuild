package com.itam.tenantadmin;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.security.TenantContext;
import com.itam.tenantadmin.dto.CreateRoleRequest;
import com.itam.tenantadmin.dto.RoleResponse;
import com.itam.tenantadmin.dto.UpdateRoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色管理（租户级）。内置角色(is_system)不可删除。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditLogService auditLogService;

    private UUID tenantId() {
        UUID t = TenantContext.getCurrentTenantId();
        if (t == null) throw new BusinessException(ResultCode.NO_PERMISSION, "无租户上下文");
        return t;
    }

    public List<RoleResponse> list() {
        UUID tid = tenantId();
        return roleRepository.findByTenantId(tid).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest req) {
        UUID tid = tenantId();
        if (roleRepository.existsByTenantIdAndCode(tid, req.code())) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已存在");
        }
        Role role = new Role();
        role.setTenantId(tid);
        role.setCode(req.code());
        role.setName(req.name());
        role.setDescription(req.description());
        role.setSystem(false);
        role.setCreatedBy(TenantContext.getCurrentUserId());
        role.setUpdatedBy(TenantContext.getCurrentUserId());
        role = roleRepository.save(role);
        auditLogService.log("ROLE_CREATE", "ROLE", role.getId().toString(), Map.of("code", req.code()));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest req) {
        UUID tid = tenantId();
        Role role = roleRepository.findByTenantIdAndId(tid, id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "角色不存在"));
        if (req.name() != null) role.setName(req.name());
        if (req.description() != null) role.setDescription(req.description());
        role.setUpdatedBy(TenantContext.getCurrentUserId());
        role = roleRepository.save(role);
        auditLogService.log("ROLE_UPDATE", "ROLE", id.toString(), null);
        return toResponse(role);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tid = tenantId();
        Role role = roleRepository.findByTenantIdAndId(tid, id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "角色不存在"));
        if (role.isSystem()) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "内置角色不可删除");
        }
        rolePermissionRepository.deleteByTenantIdAndRoleId(tid, id);
        role.setDeleted(true);
        role.setUpdatedBy(TenantContext.getCurrentUserId());
        roleRepository.save(role);
        auditLogService.log("ROLE_DELETE", "ROLE", id.toString(), null);
    }

    private RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystem(), r.getCreatedAt());
    }
}
