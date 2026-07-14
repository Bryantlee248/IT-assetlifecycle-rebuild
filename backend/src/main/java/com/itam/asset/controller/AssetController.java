package com.itam.asset.controller;

import com.itam.asset.application.AssetAppService;
import com.itam.asset.application.AssetRelationAppService;
import com.itam.asset.dto.AssetListItem;
import com.itam.asset.dto.AssetQuery;
import com.itam.asset.dto.AssetRelationDto;
import com.itam.asset.dto.AssetResponse;
import com.itam.asset.dto.CreateAssetRequest;
import com.itam.asset.dto.CreateRelationRequest;
import com.itam.asset.dto.UpdateAssetRequest;
import com.itam.common.result.ApiResponse;
import com.itam.common.result.PageResult;
import com.itam.security.JwtUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 资产控制器：列表/创建/详情/编辑/删除 + 资产关系。
 * 鉴权：读 asset:view；写 asset:create / asset:update / asset:delete。
 * tenant_id 与角色码来自 JWT principal，前端不可伪造。
 */
@RestController
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetAppService assetAppService;
    private final AssetRelationAppService assetRelationAppService;

    public AssetController(AssetAppService assetAppService, AssetRelationAppService assetRelationAppService) {
        this.assetAppService = assetAppService;
        this.assetRelationAppService = assetRelationAppService;
    }

    @GetMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:view')")
    public ApiResponse<PageResult<AssetListItem>> list(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                      @ModelAttribute AssetQuery query) {
        return ApiResponse.success(assetAppService.list(principal.getTenantId(), principal.getUserId(), roleCode(principal), query));
    }

    @PostMapping
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:create')")
    public ApiResponse<AssetResponse> create(@AuthenticationPrincipal JwtUserPrincipal principal,
                                            @Valid @RequestBody CreateAssetRequest req) {
        return ApiResponse.success(assetAppService.create(
                principal.getTenantId(), principal.getUserId(), roleCode(principal), req));
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:view')")
    public ApiResponse<AssetResponse> get(@AuthenticationPrincipal JwtUserPrincipal principal,
                                         @PathVariable UUID assetId) {
        return ApiResponse.success(assetAppService.get(principal.getTenantId(), principal.getUserId(), roleCode(principal), assetId));
    }

    @PutMapping("/{assetId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:update')")
    public ApiResponse<AssetResponse> update(@AuthenticationPrincipal JwtUserPrincipal principal,
                                            @PathVariable UUID assetId,
                                            @Valid @RequestBody UpdateAssetRequest req) {
        return ApiResponse.success(assetAppService.update(
                principal.getTenantId(), principal.getUserId(), roleCode(principal), assetId, req));
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:delete')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal JwtUserPrincipal principal,
                                   @PathVariable UUID assetId) {
        assetAppService.delete(principal.getTenantId(), principal.getUserId(), assetId);
        return ApiResponse.success();
    }

    @GetMapping("/{assetId}/relations")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:view')")
    public ApiResponse<List<AssetRelationDto>> listRelations(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                           @PathVariable UUID assetId) {
        return ApiResponse.success(assetRelationAppService.list(principal.getTenantId(), assetId));
    }

    @PostMapping("/{assetId}/relations")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:update')")
    public ApiResponse<AssetRelationDto> createRelation(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                       @PathVariable UUID assetId,
                                                       @Valid @RequestBody CreateRelationRequest req) {
        return ApiResponse.success(assetRelationAppService.create(
                principal.getTenantId(), principal.getUserId(), assetId, req));
    }

    @DeleteMapping("/{assetId}/relations/{relationId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:delete')")
    public ApiResponse<Void> deleteRelation(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @PathVariable UUID assetId,
                                           @PathVariable UUID relationId) {
        assetRelationAppService.delete(principal.getTenantId(), principal.getUserId(), assetId, relationId);
        return ApiResponse.success();
    }

    private String roleCode(JwtUserPrincipal principal) {
        return principal.getRoles() == null || principal.getRoles().isEmpty()
                ? null : principal.getRoles().iterator().next();
    }
}
