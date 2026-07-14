package com.itam.tenantadmin;

import com.itam.common.result.ApiResponse;
import com.itam.tenantadmin.dto.CreateOrgRequest;
import com.itam.tenantadmin.dto.OrgNode;
import com.itam.tenantadmin.dto.UpdateOrgRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenant/organizations")
@PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('org:list')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<OrgNode>> tree() {
        return ApiResponse.success(organizationService.tree());
    }

    @PostMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('org:create')")
    public ApiResponse<OrgNode> create(@Valid @RequestBody CreateOrgRequest req) {
        return ApiResponse.success(organizationService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('org:update')")
    public ApiResponse<OrgNode> update(@PathVariable UUID id, @Valid @RequestBody UpdateOrgRequest req) {
        return ApiResponse.success(organizationService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('org:delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        organizationService.delete(id);
        return ApiResponse.success();
    }
}
