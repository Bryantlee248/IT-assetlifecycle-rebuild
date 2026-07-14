package com.itam.asset.dto;

import java.util.UUID;

/**
 * 资产列表查询条件（绑定自请求参数）。page 从 1 开始，size 默认 20、最大 200。
 */
public class AssetQuery {

    private UUID assetTypeId;
    private String lifecycleStatus; // 逗号分隔多值
    private String keyword;         // 模糊匹配 asset_no / asset_name
    private UUID locationId;
    private UUID ownerUserId;
    private UUID ownerOrgId;
    private UUID responsibleUserId;
    private String warrantyEndFrom;
    private String warrantyEndTo;
    private String licenseEndFrom;
    private String licenseEndTo;
    private int page = 1;
    private int size = 20;
    private String sort; // field,asc|desc

    // 以下两项由应用服务按当前登录用户注入（客户端不可传入），用于数据范围过滤。
    private String dataScopeUserId; // 当前用户 ID（asset_user 用于 self/responsible 范围）
    private String dataScopeRole;   // 当前角色码

    public UUID getAssetTypeId() {
        return assetTypeId;
    }

    public void setAssetTypeId(UUID assetTypeId) {
        this.assetTypeId = assetTypeId;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getOwnerOrgId() {
        return ownerOrgId;
    }

    public void setOwnerOrgId(UUID ownerOrgId) {
        this.ownerOrgId = ownerOrgId;
    }

    public UUID getResponsibleUserId() {
        return responsibleUserId;
    }

    public void setResponsibleUserId(UUID responsibleUserId) {
        this.responsibleUserId = responsibleUserId;
    }

    public String getWarrantyEndFrom() {
        return warrantyEndFrom;
    }

    public void setWarrantyEndFrom(String warrantyEndFrom) {
        this.warrantyEndFrom = warrantyEndFrom;
    }

    public String getWarrantyEndTo() {
        return warrantyEndTo;
    }

    public void setWarrantyEndTo(String warrantyEndTo) {
        this.warrantyEndTo = warrantyEndTo;
    }

    public String getLicenseEndFrom() {
        return licenseEndFrom;
    }

    public void setLicenseEndFrom(String licenseEndFrom) {
        this.licenseEndFrom = licenseEndFrom;
    }

    public String getLicenseEndTo() {
        return licenseEndTo;
    }

    public void setLicenseEndTo(String licenseEndTo) {
        this.licenseEndTo = licenseEndTo;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getDataScopeUserId() {
        return dataScopeUserId;
    }

    public void setDataScopeUserId(String dataScopeUserId) {
        this.dataScopeUserId = dataScopeUserId;
    }

    public String getDataScopeRole() {
        return dataScopeRole;
    }

    public void setDataScopeRole(String dataScopeRole) {
        this.dataScopeRole = dataScopeRole;
    }
}
