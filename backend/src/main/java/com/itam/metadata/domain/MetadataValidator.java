package com.itam.metadata.domain;

import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.dto.CreateFieldRequest;
import com.itam.metadata.dto.UpdateFieldRequest;
import com.itam.metadata.constants.FieldType;
import com.itam.metadata.entity.FieldDefinition;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 元数据校验：字段类型合法性、唯一范围合法性、字段编码不可变性。
 */
@Component
public class MetadataValidator {

    private static final Set<String> VALID_SCOPES = Set.of("none", "tenant", "asset_type");

    public void validateFieldCreate(CreateFieldRequest req) {
        if (!FieldType.isValid(req.fieldType())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "非法字段类型: " + req.fieldType());
        }
        String scope = req.uniqueScope() == null ? "none" : req.uniqueScope();
        if (!VALID_SCOPES.contains(scope.toLowerCase())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "非法唯一范围");
        }
    }

    public void validateFieldUpdate(FieldDefinition existing, UpdateFieldRequest req) {
        if (req.fieldType() != null && !FieldType.isValid(req.fieldType())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "非法字段类型");
        }
        if (req.uniqueScope() != null && !VALID_SCOPES.contains(req.uniqueScope().toLowerCase())) {
            throw new BusinessException(ResultCode.BUSINESS_RULE_VIOLATION, "非法唯一范围");
        }
        // field_code / asset_type_id 不可修改：UpdateFieldRequest 不含这两个字段，天然不可变。
    }
}
