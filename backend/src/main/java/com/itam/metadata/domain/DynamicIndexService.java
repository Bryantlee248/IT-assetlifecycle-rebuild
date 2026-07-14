package com.itam.metadata.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 动态唯一索引服务。
 *
 * 当字段声明 unique_scope ∈ {tenant, asset_type} 且为热点物理列时，在发布事务内
 * 幂等创建部分唯一索引（IF NOT EXISTS + WHERE deleted=false），与 V3 既有 ux_* 索引同构。
 * 索引名：ux_assets_tenant_<col>_<scope>_active；scope=tenant→(tenant_id,col)，
 * scope=asset_type→(tenant_id, asset_type_id, col)。
 *
 * 失败仅记录 warn（开发/演示库默认可 DDL；若生产库收回 DDL 权限则由应用层 existsBy 兜底返回 409）。
 */
@Service
public class DynamicIndexService {

    private static final Logger log = LoggerFactory.getLogger(DynamicIndexService.class);

    private final JdbcTemplate jdbcTemplate;

    public DynamicIndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensurePartialUniqueIndex(String col, String scope) {
        if (col == null || scope == null) {
            return;
        }
        String idxName = "ux_assets_tenant_" + col + "_" + scope + "_active";
        String columns = "tenant".equalsIgnoreCase(scope)
                ? "tenant_id, " + col
                : "tenant_id, asset_type_id, " + col;
        String sql = "CREATE UNIQUE INDEX IF NOT EXISTS " + idxName
                + " ON assets (" + columns + ") WHERE deleted = false";
        try {
            jdbcTemplate.execute(sql);
            log.info("动态唯一索引就绪: {}", idxName);
        } catch (Exception e) {
            log.warn("动态唯一索引创建跳过（应用层兜底 409）: {} | {}", idxName, e.getMessage());
        }
    }
}
