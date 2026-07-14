package com.itam.metadata.application;

import com.itam.asset.constants.HotspotColumn;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.domain.FieldPermissionService;
import com.itam.metadata.domain.FieldPermissionView;
import com.itam.metadata.dto.FieldDefinitionResponse;
import com.itam.metadata.dto.RuntimeMetadataResponse;
import com.itam.metadata.entity.AssetType;
import com.itam.metadata.entity.FieldDefinition;
import com.itam.metadata.entity.FormSchema;
import com.itam.metadata.entity.ListViewSchema;
import com.itam.metadata.entity.SearchSchema;
import com.itam.metadata.repository.AssetTypeRepository;
import com.itam.metadata.repository.FieldDefinitionRepository;
import com.itam.metadata.repository.FormSchemaRepository;
import com.itam.metadata.repository.ListViewSchemaRepository;
import com.itam.metadata.repository.SearchSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时元数据聚合服务。
 * 聚合「字段定义 + form/list/search schema + 当前角色字段权限」为单一对象，
 * 前端据此渲染动态表单/列表/筛选。MVP-1 不做缓存。
 */
@Service
@RequiredArgsConstructor
public class RuntimeMetadataService {

    private final AssetTypeRepository assetTypeRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final FormSchemaRepository formSchemaRepository;
    private final ListViewSchemaRepository listViewSchemaRepository;
    private final SearchSchemaRepository searchSchemaRepository;
    private final FieldPermissionService fieldPermissionService;

    public RuntimeMetadataResponse aggregate(UUID tenantId, String roleCode, UUID typeId) {
        AssetType at = assetTypeRepository.findByTenantIdAndId(tenantId, typeId)
                .orElseThrow(() -> new BusinessException(ResultCode.ASSET_NOT_FOUND));
        List<FieldDefinition> defs = fieldDefinitionRepository
                .findByTenantIdAndAssetTypeIdOrderBySortOrderAsc(tenantId, typeId);

        Map<String, FieldPermissionView> permissions = new LinkedHashMap<>();
        for (FieldDefinition fd : defs) {
            permissions.put(fd.getFieldCode(),
                    fieldPermissionService.resolveByCode(tenantId, roleCode, fd, fd.getFieldCode()));
        }
        // P0-2/P0-3：固定物理列（热点列 + 系统列）也纳入字段权限，使前端可对固定字段做可见/只读/脱敏控制。
        // 若某固定列已存在字段定义（如 serial_no），以定义解析结果为准，不覆盖。
        for (String col : HotspotColumn.ALL) {
            permissions.putIfAbsent(col, fieldPermissionService.resolve(tenantId, roleCode, typeId, col));
        }
        for (String col : List.of("lifecycle_status", "status", "source_type", "created_at", "updated_at")) {
            permissions.putIfAbsent(col, fieldPermissionService.resolve(tenantId, roleCode, typeId, col));
        }

        Map<String, Object> form = formSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(FormSchema::getSchemaJson).orElse(Map.of());
        Map<String, Object> list = listViewSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(ListViewSchema::getSchemaJson).orElse(Map.of());
        Map<String, Object> search = searchSchemaRepository.findByTenantIdAndAssetTypeId(tenantId, typeId)
                .map(SearchSchema::getSchemaJson).orElse(Map.of());

        List<FieldDefinitionResponse> fieldResponses = defs.stream()
                .map(MetadataMapper::toFieldResponse).toList();

        return new RuntimeMetadataResponse(
                MetadataMapper.toTypeResponse(at), fieldResponses, permissions, form, list, search);
    }
}
