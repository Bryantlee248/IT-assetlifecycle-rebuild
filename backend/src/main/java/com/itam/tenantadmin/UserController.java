package com.itam.tenantadmin;

import com.itam.common.result.ApiResponse;
import com.itam.common.result.PageResult;
import com.itam.tenantadmin.dto.CreateUserRequest;
import com.itam.tenantadmin.dto.UpdateUserRequest;
import com.itam.tenantadmin.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenant/users")
@PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('user:list')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<UserResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.list(page, size, keyword));
    }

    @PostMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('user:create')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.success(userService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('user:update')")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.success(userService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('user:delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ApiResponse.success();
    }
}
