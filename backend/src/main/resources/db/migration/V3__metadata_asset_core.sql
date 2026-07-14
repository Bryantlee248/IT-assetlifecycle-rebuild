-- =============================================================================
-- MVP-1 元数据与资产核心：建表迁移（V3）
-- 在 V1(平台 schema) / V2(平台种子) 之后续接。
-- 新建 9 张表：asset_types / field_definitions / form_schemas / list_view_schemas /
--   search_schemas / assets / asset_relations / locations / field_permission_rules
-- 索引命名严格采用用户给定 ux_* 名称；所有唯一索引均带 WHERE deleted = false（部分唯一索引）。
-- 注意：server.servlet.context-path=/api，但 SQL 与 context-path 无关。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) asset_types（资产类型，树形 parent_id 自引用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_types (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    parent_id             UUID,
    type_code             VARCHAR(64) NOT NULL,
    type_name             VARCHAR(128) NOT NULL,
    asset_kind            VARCHAR(32) NOT NULL,            -- tangible / intangible
    lifecycle_template_id UUID,
    icon                  VARCHAR(64),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order            INT NOT NULL DEFAULT 0,
    created_by            UUID,
    updated_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted               BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_types_tenant_code_active
    ON asset_types (tenant_id, type_code) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2) field_definitions（字段定义）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS field_definitions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    asset_type_id    UUID NOT NULL,
    field_code       VARCHAR(128) NOT NULL,
    field_name       VARCHAR(128) NOT NULL,
    field_type       VARCHAR(32) NOT NULL,               -- 16 值枚举见 FieldType
    storage_type     VARCHAR(32) NOT NULL DEFAULT 'jsonb', -- physical / jsonb / encrypted
    physical_column  VARCHAR(64),
    required         BOOLEAN NOT NULL DEFAULT FALSE,
    unique_scope     VARCHAR(32) NOT NULL DEFAULT 'none', -- none / tenant / asset_type
    default_value    JSONB,
    validation_rule  JSONB,
    data_source      JSONB,
    searchable       BOOLEAN NOT NULL DEFAULT FALSE,
    sortable         BOOLEAN NOT NULL DEFAULT FALSE,
    indexed          BOOLEAN NOT NULL DEFAULT FALSE,
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    editable         BOOLEAN NOT NULL DEFAULT TRUE,
    sensitive        BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted        BOOLEAN NOT NULL DEFAULT FALSE,
    mask_rule        VARCHAR(64),
    sort_order       INT NOT NULL DEFAULT 0,
    status           VARCHAR(32) NOT NULL DEFAULT 'enabled', -- enabled / disabled
    created_by       UUID,
    updated_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_field_definitions_tenant_type_code_active
    ON field_definitions (tenant_id, asset_type_id, field_code) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 3) form_schemas（表单配置 JSONB）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_schemas (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    asset_type_id UUID NOT NULL,
    schema_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    version      INT NOT NULL DEFAULT 1,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_form_schemas_tenant_type
    ON form_schemas (tenant_id, asset_type_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 4) list_view_schemas（列表配置 JSONB）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS list_view_schemas (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    asset_type_id UUID NOT NULL,
    schema_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    version      INT NOT NULL DEFAULT 1,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_list_view_schemas_tenant_type
    ON list_view_schemas (tenant_id, asset_type_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 5) search_schemas（查询配置 JSONB）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS search_schemas (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    asset_type_id UUID NOT NULL,
    schema_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    version      INT NOT NULL DEFAULT 1,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_search_schemas_tenant_type
    ON search_schemas (tenant_id, asset_type_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 6) assets（资产主对象：16 热点物理列 + attributes JSONB）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assets (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    asset_no            VARCHAR(64) NOT NULL,
    asset_name          VARCHAR(200) NOT NULL,
    asset_kind          VARCHAR(32) NOT NULL,
    asset_type_id       UUID NOT NULL,
    lifecycle_status    VARCHAR(64) NOT NULL,
    owner_user_id       UUID,
    owner_org_id        UUID,
    location_id         UUID,
    cost_center_id      UUID,
    responsible_user_id UUID,
    serial_no           VARCHAR(128),
    brand               VARCHAR(128),
    model               VARCHAR(128),
    vendor              VARCHAR(128),
    warranty_end_date   DATE,
    license_end_date    DATE,
    source_type         VARCHAR(32) NOT NULL DEFAULT 'manual',
    sync_source         VARCHAR(64),
    metadata_version    INT NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'active',
    attributes          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by          UUID,
    updated_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);
-- 部分唯一索引（热点唯一字段）：仅租户内未删除行唯一
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_tenant_asset_no_active
    ON assets (tenant_id, asset_no) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_tenant_serial_no_active
    ON assets (tenant_id, serial_no) WHERE deleted = FALSE AND serial_no IS NOT NULL;
-- 常用查询索引
CREATE INDEX IF NOT EXISTS idx_assets_tenant_type
    ON assets (tenant_id, asset_type_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_assets_tenant_status
    ON assets (tenant_id, lifecycle_status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_assets_tenant_location
    ON assets (tenant_id, location_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_assets_warranty_end
    ON assets (tenant_id, warranty_end_date) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_assets_license_end
    ON assets (tenant_id, license_end_date) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_assets_attributes_gin
    ON assets USING gin (attributes);

-- -----------------------------------------------------------------------------
-- 7) asset_relations（资产关系）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_relations (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    source_asset_id UUID NOT NULL,
    target_asset_id UUID NOT NULL,
    relation_type  VARCHAR(64) NOT NULL,
    description    TEXT,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_asset_relations_tenant_source
    ON asset_relations (tenant_id, source_asset_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 8) locations（位置，最小表，支持位置选择器）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS locations (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    parent_id  UUID,
    name       VARCHAR(128) NOT NULL,
    code       VARCHAR(64),
    path       TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_locations_tenant_parent
    ON locations (tenant_id, parent_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 9) field_permission_rules（字段权限规则表，MVP-1 不喂数据，由内存默认矩阵实现）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS field_permission_rules (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    role_id       UUID NOT NULL,
    asset_type_id UUID,
    field_code    VARCHAR(128) NOT NULL,
    visible       BOOLEAN NOT NULL DEFAULT TRUE,
    editable      BOOLEAN NOT NULL DEFAULT FALSE,
    masked        BOOLEAN NOT NULL DEFAULT FALSE,
    exportable    BOOLEAN NOT NULL DEFAULT FALSE,
    mask_rule     VARCHAR(64),
    condition_rule JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_field_permission_role_type_field_active
    ON field_permission_rules (tenant_id, role_id, asset_type_id, field_code) WHERE deleted = FALSE;
