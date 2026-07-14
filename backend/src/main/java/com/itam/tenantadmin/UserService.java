package com.itam.tenantadmin;

import com.itam.audit.AuditLogService;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.PageResult;
import com.itam.common.result.ResultCode;
import com.itam.platform.PlatformUser;
import com.itam.platform.PlatformUserRepository;
import com.itam.security.TenantContext;
import com.itam.tenantadmin.dto.CreateUserRequest;
import com.itam.tenantadmin.dto.UpdateUserRequest;
import com.itam.tenantadmin.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 租户用户管理：创建/列表/修改/停用。每个租户用户对应一个 platform_user + tenant_user 关联。
 * 资源 id 为 tenant_user.id（租户作用域）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final PlatformUserRepository platformUserRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    private UUID tenantId() {
        UUID t = TenantContext.getCurrentTenantId();
        if (t == null) throw new BusinessException(ResultCode.NO_PERMISSION, "无租户上下文");
        return t;
    }

    public PageResult<UserResponse> list(int page, int size, String keyword) {
        UUID tid = tenantId();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<TenantUser> result = tenantUserRepository.findByTenantId(tid, pageable);
        List<UserResponse> list = result.getContent().stream().map(tu -> toResponse(tid, tu)).toList();
        return PageResult.of(page, size, result.getTotalElements(), list);
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        UUID tid = tenantId();
        if (platformUserRepository.existsByUsername(req.username())) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        if (req.roleId() != null && roleRepository.findByTenantIdAndId(tid, req.roleId()).isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        PlatformUser pu = new PlatformUser();
        pu.setUsername(req.username());
        pu.setPasswordHash(passwordEncoder.encode(req.password()));
        pu.setDisplayName(req.displayName());
        pu.setEmail(req.email());
        pu.setPhone(req.phone());
        pu.setStatus(req.status() == null ? "ACTIVE" : req.status());
        pu.setMustChangePassword(true);
        pu.setCreatedBy(TenantContext.getCurrentUserId());
        pu.setUpdatedBy(TenantContext.getCurrentUserId());
        pu = platformUserRepository.save(pu);

        TenantUser tu = new TenantUser();
        tu.setTenantId(tid);
        tu.setPlatformUserId(pu.getId());
        tu.setRoleId(req.roleId());
        tu.setStatus("ACTIVE");
        tu.setCreatedBy(TenantContext.getCurrentUserId());
        tu.setUpdatedBy(TenantContext.getCurrentUserId());
        tu = tenantUserRepository.save(tu);

        auditLogService.log("USER_CREATE", "USER", tu.getId().toString(), Map.of("username", req.username()));
        return toResponse(tid, tu);
    }

    @Transactional
    public UserResponse update(UUID tenantUserId, UpdateUserRequest req) {
        UUID tid = tenantId();
        TenantUser tu = tenantUserRepository.findByTenantIdAndId(tid, tenantUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        if (req.roleId() != null && roleRepository.findByTenantIdAndId(tid, req.roleId()).isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        PlatformUser pu = platformUserRepository.findById(tu.getPlatformUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        if (req.displayName() != null) pu.setDisplayName(req.displayName());
        if (req.email() != null) pu.setEmail(req.email());
        if (req.phone() != null) pu.setPhone(req.phone());
        if (req.status() != null) pu.setStatus(req.status());
        pu.setUpdatedBy(TenantContext.getCurrentUserId());
        platformUserRepository.save(pu);

        if (req.roleId() != null) tu.setRoleId(req.roleId());
        if (req.status() != null) tu.setStatus(req.status());
        tu.setUpdatedBy(TenantContext.getCurrentUserId());
        tu = tenantUserRepository.save(tu);

        auditLogService.log("USER_UPDATE", "USER", tenantUserId.toString(), null);
        return toResponse(tid, tu);
    }

    @Transactional
    public void delete(UUID tenantUserId) {
        UUID tid = tenantId();
        TenantUser tu = tenantUserRepository.findByTenantIdAndId(tid, tenantUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
        tu.setDeleted(true);
        tu.setStatus("DISABLED");
        tu.setUpdatedBy(TenantContext.getCurrentUserId());
        tenantUserRepository.save(tu);
        auditLogService.log("USER_DELETE", "USER", tenantUserId.toString(), null);
    }

    private UserResponse toResponse(UUID tid, TenantUser tu) {
        PlatformUser pu = platformUserRepository.findById(tu.getPlatformUserId()).orElse(new PlatformUser());
        String roleName = null;
        if (tu.getRoleId() != null) {
            roleName = roleRepository.findByTenantIdAndId(tid, tu.getRoleId())
                    .map(Role::getName).orElse(null);
        }
        return new UserResponse(tu.getId(), pu.getUsername(), pu.getDisplayName(),
                pu.getEmail(), pu.getPhone(), tu.getStatus(), tu.getRoleId(), roleName, tid);
    }
}
