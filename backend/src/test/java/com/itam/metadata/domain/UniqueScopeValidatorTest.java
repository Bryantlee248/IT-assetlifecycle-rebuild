package com.itam.metadata.domain;

import com.itam.common.exception.BusinessException;
import com.itam.common.result.ResultCode;
import com.itam.metadata.entity.FieldDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 字段唯一性发布校验单元测试（硬约束）：非热点字段不得声明唯一；物理列须命中热点白名单。
 */
class UniqueScopeValidatorTest {

    private final UniqueScopeValidator validator = new UniqueScopeValidator();

    private FieldDefinition fd(String storageType, String physicalColumn, String uniqueScope) {
        FieldDefinition f = new FieldDefinition();
        f.setStorageType(storageType);
        f.setPhysicalColumn(physicalColumn);
        f.setUniqueScope(uniqueScope);
        return f;
    }

    @Test
    void none_scope_passes() {
        validator.validate(fd("jsonb", null, "none"));
    }

    @Test
    void physical_hotspot_unique_passes() {
        validator.validate(fd("physical", "serial_no", "tenant"));
    }

    @Test
    void jsonb_with_unique_scope_rejected() {
        FieldDefinition f = fd("jsonb", null, "tenant");
        assertThatThrownBy(() -> validator.validate(f))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.FIELD_UNIQUE_REJECTED);
    }

    @Test
    void physical_non_hotspot_unique_rejected() {
        FieldDefinition f = fd("physical", "some_random_col", "tenant");
        assertThatThrownBy(() -> validator.validate(f))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.FIELD_UNIQUE_REJECTED);
    }

    @Test
    void physical_non_hotspot_storage_rejected() {
        FieldDefinition f = fd("physical", "unknown_col", "none");
        assertThatThrownBy(() -> validator.validateStorage(f))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void null_scope_treated_as_none() {
        FieldDefinition f = fd("jsonb", null, null);
        validator.validate(f); // 不抛异常
    }
}
