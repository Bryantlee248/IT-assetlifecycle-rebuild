package com.itam.metadata.domain;

/**
 * 字段权限解析结果（单一真相）。
 * 由 FieldPermissionService.resolve 产出，供 AssetAssembler 与 RuntimeMetadataService 消费。
 */
public record FieldPermissionView(
        boolean visible,
        boolean editable,
        boolean masked,
        boolean exportable,
        String maskRule) {

    /** 无权限视图（不可见、不可编辑、脱敏、不可导出）。 */
    public static FieldPermissionView denied() {
        return new FieldPermissionView(false, false, true, false, null);
    }
}
