package com.itam.tenantadmin;

import com.itam.common.result.ApiResponse;
import com.itam.tenantadmin.dto.CreateRoleRequest;
import com.itam.tenantadmin.dto.RolePermissionResponse;
import com.itam.tenantadmin.dto.RoleResponse;
import com.itam.tenantadmin.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenant/roles")
@PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:list')")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    public RoleController(RoleService roleService, RolePermissionService rolePermissionService) {
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleService.list());
    }

    @PostMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:create')")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest req) {
        return ApiResponse.success(roleService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:update')")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
        return ApiResponse.success(roleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:assign')")
    public ApiResponse<RolePermissionResponse> getPermissions(@PathVariable UUID id) {
        return ApiResponse.success(rolePermissionService.get(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('role:assign')")
    public ApiResponse<RolePermissionResponse> setPermissions(@PathVariable UUID id,
                                                             @RequestBody List<String> permissions) {
        return ApiResponse.success(rolePermissionService.set(id, permissions));
    }
}
