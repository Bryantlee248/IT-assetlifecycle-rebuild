package com.itam.asset.constants;

import java.util.Set;

/**
 * 热点物理列白名单。
 * 字段定义 storage_type='physical' 时 physical_column 必须落在白名单；
 * 否则若声明唯一（unique_scope != none）将被 UniqueScopeValidator 拒绝发布（422）。
 */
public final class HotspotColumn {

    public static final String ASSET_NO = "asset_no";
    public static final String ASSET_NAME = "asset_name";
    public static final String ASSET_KIND = "asset_kind";
    public static final String ASSET_TYPE_ID = "asset_type_id";
    public static final String LIFECYCLE_STATUS = "lifecycle_status";
    public static final String OWNER_USER_ID = "owner_user_id";
    public static final String OWNER_ORG_ID = "owner_org_id";
    public static final String LOCATION_ID = "location_id";
    public static final String COST_CENTER_ID = "cost_center_id";
    public static final String RESPONSIBLE_USER_ID = "responsible_user_id";
    public static final String SERIAL_NO = "serial_no";
    public static final String BRAND = "brand";
    public static final String MODEL = "model";
    public static final String VENDOR = "vendor";
    public static final String WARRANTY_END_DATE = "warranty_end_date";
    public static final String LICENSE_END_DATE = "license_end_date";

    public static final Set<String> ALL = Set.of(
            ASSET_NO, ASSET_NAME, ASSET_KIND, ASSET_TYPE_ID, LIFECYCLE_STATUS,
            OWNER_USER_ID, OWNER_ORG_ID, LOCATION_ID, COST_CENTER_ID, RESPONSIBLE_USER_ID,
            SERIAL_NO, BRAND, MODEL, VENDOR, WARRANTY_END_DATE, LICENSE_END_DATE);

    private HotspotColumn() {
    }

    public static boolean contains(String column) {
        return ALL.contains(column);
    }
}
