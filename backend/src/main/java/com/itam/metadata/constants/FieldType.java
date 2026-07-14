package com.itam.metadata.constants;

import java.util.Set;

/**
 * 字段类型权威枚举（16 值，varchar 存储）。
 * 以该枚举为单一存储真相；前端可据此渲染不同输入控件。
 */
public enum FieldType {
    TEXT,
    TEXTAREA,
    NUMBER,
    DECIMAL,
    DATE,
    DATETIME,
    ENUM,
    MULTI_ENUM,
    BOOLEAN,
    USER,
    ORG,
    LOCATION,
    ASSET_RELATION,
    URL,
    JSON,
    FILE;

    private static final Set<String> ALL = Set.of(
            "text", "textarea", "number", "decimal", "date", "datetime",
            "enum", "multi_enum", "boolean", "user", "org", "location",
            "asset_relation", "url", "json", "file");

    public static boolean isValid(String code) {
        return code != null && ALL.contains(code.toLowerCase());
    }

    public String code() {
        return name().toLowerCase();
    }
}
