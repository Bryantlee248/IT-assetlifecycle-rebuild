package com.itam.metadata.domain;

import com.itam.asset.constants.HotspotColumn;
import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.entity.FieldDefinition;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 字段唯一性发布校验（硬约束）。
 *
 * 规则：unique_scope != none 时，字段必须是"热点物理列"（storage_type=physical 且
 * physical_column 命中 HotspotColumn 白名单）；否则拒绝发布为 422 FIELD_UNIQUE_REJECTED。
 * 理由：非热点（jsonb/扩展）字段无法在数据库层建立可落地的部分唯一索引，不能只靠应用层兜底。
 *
 * 同时校验：storage_type=physical 时 physical_column 必须落在热点白名单。
 */
@Service
public class UniqueScopeValidator {

    private static final Set<String> VALID_SCOPES = Set.of("none", "tenant", "asset_type");

    public void validate(FieldDefinition fd) {
        String scope = fd.getUniqueScope() == null ? "none" : fd.getUniqueScope();
        if (!VALID_SCOPES.contains(scope.toLowerCase())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "非法唯一范围");
        }
        if ("none".equalsIgnoreCase(scope)) {
            return;
        }
        boolean physicalHotspot = "physical".equalsIgnoreCase(fd.getStorageType())
                && fd.getPhysicalColumn() != null
                && HotspotColumn.contains(fd.getPhysicalColumn());
        if (!physicalHotspot) {
            throw new BusinessException(ResultCode.FIELD_UNIQUE_REJECTED);
        }
    }

    public void validateStorage(FieldDefinition fd) {
        if ("physical".equalsIgnoreCase(fd.getStorageType())
                && (fd.getPhysicalColumn() == null || !HotspotColumn.contains(fd.getPhysicalColumn()))) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "物理字段必须映射到热点物理列");
        }
    }
}
