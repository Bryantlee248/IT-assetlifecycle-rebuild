package com.itam.auth;

import com.itam.auth.dto.*;
import com.itam.common.result.ApiResponse;
import com.itam.security.JwtUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.success(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal JwtUserPrincipal principal) {
        authService.logout(principal);
        return ApiResponse.success();
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal JwtUserPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal, req);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(authService.me(principal));
    }

    @GetMapping("/menu")
    public ApiResponse<List<MenuNode>> menu(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(authService.menu(principal));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(authService.permissions(principal));
    }

    @GetMapping("/tenants")
    public ApiResponse<List<TenantBrief>> tenants(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(authService.accessibleTenants(principal));
    }

    @PostMapping("/tenant-switch")
    public ApiResponse<TokenResponse> switchTenant(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                  @Valid @RequestBody SwitchTenantRequest req) {
        return ApiResponse.success(authService.switchTenant(principal, req.tenantId()));
    }

    public record SwitchTenantRequest(UUID tenantId) {
    }
}
