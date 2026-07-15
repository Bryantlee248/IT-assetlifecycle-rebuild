package com.itam.metadata.controller;

import com.itam.common.result.ApiResponse;
import com.itam.metadata.application.AssetTypeAppService;
import com.itam.metadata.application.FieldAppService;
import com.itam.metadata.application.RuntimeMetadataService;
import com.itam.metadata.application.SchemaAppService;
import com.itam.metadata.dto.AssetTypeNode;
import com.itam.metadata.dto.AssetTypeResponse;
import com.itam.metadata.dto.CreateAssetTypeRequest;
import com.itam.metadata.dto.CreateFieldRequest;
import com.itam.metadata.dto.FieldDefinitionResponse;
import com.itam.metadata.dto.FormSchemaResponse;
import com.itam.metadata.dto.ListViewResponse;
import com.itam.metadata.dto.LocationNode;
import com.itam.metadata.dto.RuntimeMetadataResponse;
import com.itam.metadata.dto.SearchSchemaResponse;
import com.itam.metadata.dto.UpdateAssetTypeRequest;
import com.itam.metadata.dto.UpdateFieldRequest;
import com.itam.metadata.entity.Location;
import com.itam.metadata.repository.LocationRepository;
import com.itam.security.JwtUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 元数据控制器：资产类型 / 字段定义 / 表单·列表·查询配置 / 运行时元数据聚合 / 位置树。
 *
 * 鉴权：配置类接口要求 metadata:manage；运行时元数据聚合与位置树要求 asset:view（资产页依赖）。
 * 所有请求依赖 JWT 解析的 principal，tenant_id 与角色码均来自 principal，前端不可伪造。
 */
@RestController
@RequestMapping("/v1/metadata")
public class MetadataController {

    private final AssetTypeAppService assetTypeAppService;
    private final FieldAppService fieldAppService;
    private final SchemaAppService schemaAppService;
    private final RuntimeMetadataService runtimeMetadataService;
    private final LocationRepository locationRepository;

    public MetadataController(AssetTypeAppService assetTypeAppService,
                             FieldAppService fieldAppService,
                             SchemaAppService schemaAppService,
                             RuntimeMetadataService runtimeMetadataService,
                             LocationRepository locationRepository) {
        this.assetTypeAppService = assetTypeAppService;
        this.fieldAppService = fieldAppService;
        this.schemaAppService = schemaAppService;
        this.runtimeMetadataService = runtimeMetadataService;
        this.locationRepository = locationRepository;
    }

    // ===== 资产类型 =====
    @GetMapping("/asset-types/tree")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<List<AssetTypeNode>> assetTypeTree(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(assetTypeAppService.tree(principal.getTenantId()));
    }

    @PostMapping("/asset-types")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<AssetTypeResponse> createAssetType(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                         @Valid @RequestBody CreateAssetTypeRequest req) {
        return ApiResponse.success(assetTypeAppService.create(principal.getTenantId(), principal.getUserId(), req));
    }

    @PutMapping("/asset-types/{typeId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<AssetTypeResponse> updateAssetType(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                         @PathVariable UUID typeId,
                                                         @Valid @RequestBody UpdateAssetTypeRequest req) {
        return ApiResponse.success(assetTypeAppService.update(principal.getTenantId(), principal.getUserId(), typeId, req));
    }

    @PatchMapping("/asset-types/{typeId}/status")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<Void> setAssetTypeStatus(@AuthenticationPrincipal JwtUserPrincipal principal,
                                               @PathVariable UUID typeId,
                                               @RequestParam boolean enabled) {
        assetTypeAppService.setStatus(principal.getTenantId(), principal.getUserId(), typeId, enabled);
        return ApiResponse.success();
    }

    // ===== 字段定义 =====
    @GetMapping("/asset-types/{typeId}/fields")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<List<FieldDefinitionResponse>> listFields(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                               @PathVariable UUID typeId) {
        return ApiResponse.success(fieldAppService.listFields(principal.getTenantId(), typeId));
    }

    @PostMapping("/asset-types/{typeId}/fields")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<FieldDefinitionResponse> createField(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                          @PathVariable UUID typeId,
                                                          @Valid @RequestBody CreateFieldRequest req) {
        return ApiResponse.success(fieldAppService.createField(principal.getTenantId(), principal.getUserId(), typeId, req));
    }

    @PutMapping("/fields/{fieldId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<FieldDefinitionResponse> updateField(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                          @PathVariable UUID fieldId,
                                                          @Valid @RequestBody UpdateFieldRequest req) {
        return ApiResponse.success(fieldAppService.updateField(principal.getTenantId(), principal.getUserId(), fieldId, req));
    }

    @PatchMapping("/fields/{fieldId}/status")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<Void> setFieldStatus(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @PathVariable UUID fieldId,
                                           @RequestParam boolean enabled) {
        fieldAppService.setFieldStatus(principal.getTenantId(), principal.getUserId(), fieldId, enabled);
        return ApiResponse.success();
    }

    // ===== 表单 / 列表 / 查询 配置 =====
    @GetMapping("/asset-types/{typeId}/form-schema")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<FormSchemaResponse> getFormSchema(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                       @PathVariable UUID typeId) {
        return ApiResponse.success(schemaAppService.getFormSchema(principal.getTenantId(), typeId));
    }

    @PutMapping("/asset-types/{typeId}/form-schema")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<FormSchemaResponse> putFormSchema(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                       @PathVariable UUID typeId,
                                                       @RequestBody Map<String, Object> schemaJson) {
        return ApiResponse.success(schemaAppService.putFormSchema(principal.getTenantId(), principal.getUserId(), typeId, schemaJson));
    }

    @GetMapping("/asset-types/{typeId}/list-view")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<ListViewResponse> getListView(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                    @PathVariable UUID typeId) {
        return ApiResponse.success(schemaAppService.getListView(principal.getTenantId(), typeId));
    }

    @PutMapping("/asset-types/{typeId}/list-view")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<ListViewResponse> putListView(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                   @PathVariable UUID typeId,
                                                   @RequestBody Map<String, Object> schemaJson) {
        return ApiResponse.success(schemaAppService.putListView(principal.getTenantId(), principal.getUserId(), typeId, schemaJson));
    }

    @GetMapping("/asset-types/{typeId}/search-schema")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<SearchSchemaResponse> getSearchSchema(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                           @PathVariable UUID typeId) {
        return ApiResponse.success(schemaAppService.getSearchSchema(principal.getTenantId(), typeId));
    }

    @PutMapping("/asset-types/{typeId}/search-schema")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('metadata:manage')")
    public ApiResponse<SearchSchemaResponse> putSearchSchema(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                           @PathVariable UUID typeId,
                                                           @RequestBody Map<String, Object> schemaJson) {
        return ApiResponse.success(schemaAppService.putSearchSchema(principal.getTenantId(), principal.getUserId(), typeId, schemaJson));
    }

    // ===== 运行时元数据聚合（前端渲染驱动）=====
    @GetMapping("/runtime/asset-types/{typeId}")
    @PreAuthorize("principal.userType.name() == 'TENANT' and (hasAuthority('asset:view') or hasAuthority('metadata:manage'))")
    public ApiResponse<RuntimeMetadataResponse> runtimeMetadata(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                              @PathVariable UUID typeId) {
        return ApiResponse.success(runtimeMetadataService.aggregate(
                principal.getTenantId(), roleCode(principal), typeId));
    }

    // ===== 位置树（位置选择器数据源）=====
    @GetMapping("/locations/tree")
    @PreAuthorize("principal.userType.name() == 'TENANT' and hasAuthority('asset:view')")
    public ApiResponse<List<LocationNode>> locationTree(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(buildLocationTree(principal.getTenantId()));
    }

    private List<LocationNode> buildLocationTree(UUID tenantId) {
        List<Location> all = locationRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        Map<UUID, List<Location>> childrenMap = new LinkedHashMap<>();
        List<Location> roots = new ArrayList<>();
        for (Location loc : all) {
            if (loc.getParentId() == null) {
                roots.add(loc);
            } else {
                childrenMap.computeIfAbsent(loc.getParentId(), k -> new ArrayList<>()).add(loc);
            }
        }
        List<LocationNode> result = new ArrayList<>();
        for (Location r : roots) {
            result.add(toNode(r, childrenMap));
        }
        return result;
    }

    private LocationNode toNode(Location loc, Map<UUID, List<Location>> childrenMap) {
        List<LocationNode> kids = new ArrayList<>();
        for (Location c : childrenMap.getOrDefault(loc.getId(), List.of())) {
            kids.add(toNode(c, childrenMap));
        }
        return new LocationNode(loc.getId(), loc.getName(), loc.getCode(), loc.getPath(), loc.getSortOrder(), kids);
    }

    private String roleCode(JwtUserPrincipal principal) {
        return principal.getRoles() == null || principal.getRoles().isEmpty()
                ? null : principal.getRoles().iterator().next();
    }
}
