package com.itam.asset.repository;

import com.itam.asset.dto.AssetQuery;
import com.itam.asset.entity.Asset;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 资产列表动态查询条件构造（多条件可选过滤）。
 * tenant_id 强制等于；@Where(deleted=false) 已由实体层保证软删隔离。
 */
public final class AssetSpecifications {

    private AssetSpecifications() {
    }

    public static Specification<Asset> byQuery(UUID tenantId, AssetQuery q) {
        return (Root<Asset> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (q.getAssetTypeId() != null) {
                predicates.add(cb.equal(root.get("assetTypeId"), q.getAssetTypeId()));
            }
            if (q.getLifecycleStatus() != null && !q.getLifecycleStatus().isBlank()) {
                List<Predicate> statusPreds = new ArrayList<>();
                for (String s : q.getLifecycleStatus().split(",")) {
                    if (!s.isBlank()) {
                        statusPreds.add(cb.equal(root.get("lifecycleStatus"), s.trim()));
                    }
                }
                if (!statusPreds.isEmpty()) {
                    predicates.add(cb.or(statusPreds.toArray(new Predicate[0])));
                }
            }
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                String kw = "%" + q.getKeyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("assetNo"), kw),
                        cb.like(root.get("assetName"), kw)));
            }
            if (q.getLocationId() != null) {
                predicates.add(cb.equal(root.get("locationId"), q.getLocationId()));
            }
            if (q.getOwnerUserId() != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), q.getOwnerUserId()));
            }
            if (q.getOwnerOrgId() != null) {
                predicates.add(cb.equal(root.get("ownerOrgId"), q.getOwnerOrgId()));
            }
            if (q.getResponsibleUserId() != null) {
                predicates.add(cb.equal(root.get("responsibleUserId"), q.getResponsibleUserId()));
            }
            addDateRange(predicates, root, cb, "warrantyEndDate", q.getWarrantyEndFrom(), q.getWarrantyEndTo());
            addDateRange(predicates, root, cb, "licenseEndDate", q.getLicenseEndFrom(), q.getLicenseEndTo());

            // P0-2：数据范围（asset_user 仅可见本人/责任人相关资产；其余角色按租户范围）。
            if ("asset_user".equals(q.getDataScopeRole()) && q.getDataScopeUserId() != null
                    && !q.getDataScopeUserId().isBlank()) {
                UUID me = UUID.fromString(q.getDataScopeUserId());
                predicates.add(cb.or(
                        cb.equal(root.get("ownerUserId"), me),
                        cb.equal(root.get("responsibleUserId"), me)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addDateRange(List<Predicate> predicates, Root<Asset> root,
                                     CriteriaBuilder cb, String field, String from, String to) {
        if (from != null && !from.isBlank()) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field).as(LocalDate.class), LocalDate.parse(from)));
        }
        if (to != null && !to.isBlank()) {
            predicates.add(cb.lessThanOrEqualTo(root.get(field).as(LocalDate.class), LocalDate.parse(to)));
        }
    }
}
