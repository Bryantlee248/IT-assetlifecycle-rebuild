package com.itam.platform;

import com.itam.common.result.ApiResponse;
import com.itam.common.result.PageResult;
import com.itam.platform.dto.CreateTenantRequest;
import com.itam.platform.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/platform/tenants")
@PreAuthorize("principal.userType.name() == 'PLATFORM' and hasAuthority('tenant:list')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("principal.userType.name() == 'PLATFORM' and hasAuthority('tenant:create')")
    public ApiResponse<TenantResponse> create(@Valid @RequestBody CreateTenantRequest req) {
        return ApiResponse.success(tenantService.create(req));
    }

    @GetMapping
    public ApiResponse<PageResult<TenantResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(tenantService.list(page, size, keyword));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("principal.userType.name() == 'PLATFORM' and hasAuthority('tenant:disable')")
    public ApiResponse<Void> disable(@PathVariable UUID id) {
        tenantService.disable(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("principal.userType.name() == 'PLATFORM' and hasAuthority('tenant:enable')")
    public ApiResponse<Void> enable(@PathVariable UUID id) {
        tenantService.enable(id);
        return ApiResponse.success();
    }
}
