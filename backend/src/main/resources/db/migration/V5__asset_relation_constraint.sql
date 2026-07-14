-- =============================================================================
-- V5：资产关系类型枚举约束（P0-4）
--   - asset_relations.relation_type 限定为白名单，非法值由 DB 层兜底拒绝
--   - 应用层（AssetRelationAppService）已做同等校验与自环拒绝，本迁移为双保险
--   - 纯增量迁移，不修改 V1~V4 既有结构
-- =============================================================================

ALTER TABLE asset_relations
    ADD CONSTRAINT chk_asset_relation_type
    CHECK (relation_type IN ('installed_on', 'binds_to', 'depends_on', 'located_in', 'uses'));
