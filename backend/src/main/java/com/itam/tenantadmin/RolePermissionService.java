package com.itam.tenantadmin;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.security.TenantContext;
import com.itam.tenantadmin.dto.RolePermissionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色权限配置（租户级）。仅允许配置权限目录中存在的权限码。
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    private UUID tenantId() {
        UUID t = TenantContext.getCurrentTenantId();
        if (t == null) throw new BusinessException(ResultCode.NO_PERMISSION, "无租户上下文");
        return t;
    }

    public RolePermissionResponse get(UUID roleId) {
        UUID tid = tenantId();
        if (roleRepository.findByTenantIdAndId(tid, roleId).isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        List<String> codes = rolePermissionRepository.findByTenantIdAndRoleId(tid, roleId).stream()
                .map(rp -> rp.getPermissionCode()).sorted().toList();
        return new RolePermissionResponse(roleId, codes);
    }

    @Transactional
    public RolePermissionResponse set(UUID roleId, List<String> codes) {
        UUID tid = tenantId();
        if (roleRepository.findByTenantIdAndId(tid, roleId).isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        Set<String> requested = codes == null ? Set.of() : new HashSet<>(codes);
        Set<String> validCodes = permissionRepository.findByCodeIn(new ArrayList<>(requested)).stream()
                .map(c -> c.getCode()).collect(Collectors.toSet());

        rolePermissionRepository.deleteByTenantIdAndRoleId(tid, roleId);
        UUID operator = TenantContext.getCurrentUserId();
        for (String code : validCodes) {
            RolePermission rp = new RolePermission();
            rp.setTenantId(tid);
            rp.setRoleId(roleId);
            rp.setPermissionCode(code);
            rp.setCreatedBy(operator);
            rp.setUpdatedBy(operator);
            rolePermissionRepository.save(rp);
        }
        auditLogService.log("ROLE_PERMISSION_SET", "ROLE", roleId.toString(), Map.of("count", validCodes.size()));
        return new RolePermissionResponse(roleId, validCodes.stream().sorted().toList());
    }
}
