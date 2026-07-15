-- =============================================================================
-- MVP-2 生命周期状态机与动作闭环：建表迁移（V6）
-- 在 V1(平台底座) / V2(平台种子) / V3(元数据资产核心) / V4(元数据资产种子)
--   / V5(资产关系约束) 之后续接。
-- 新建 4 张表：lifecycle_templates / lifecycle_states / lifecycle_transitions /
--   lifecycle_events（均继承 TenantEntity 公共列）。
-- 索引沿用 MVP-1 约定：ux_* 唯一索引均 WHERE deleted = false（部分唯一）且
--   IF NOT EXISTS 幂等；idx_* 查询索引同。
-- JSONB：guard_rule / form_data / attachment_ids 默认 '{}'::jsonb / '[]'::jsonb。
-- 不建物理外键（template_id / asset_id / transition_id）仅应用层校验同租户。
-- 注意：server.servlet.context-path=/api，但 SQL 与 context-path 无关。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) lifecycle_templates（生命周期模板）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lifecycle_templates (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    name        VARCHAR(128) NOT NULL,
    asset_kind  VARCHAR(32),                     -- 兜底匹配键：tangible / intangible
    description TEXT,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_lifecycle_templates_tenant_name_active
    ON lifecycle_templates (tenant_id, name) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2) lifecycle_states（状态定义）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lifecycle_states (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    template_id UUID NOT NULL,                   -- -> lifecycle_templates.id（逻辑外键）
    state_code  VARCHAR(64) NOT NULL,
    state_name  VARCHAR(128) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_initial  BOOLEAN NOT NULL DEFAULT FALSE,  -- 仅 planned 为 true
    description TEXT,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_lifecycle_states_tenant_template_code_active
    ON lifecycle_states (tenant_id, template_id, state_code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_lifecycle_states_tenant_template
    ON lifecycle_states (tenant_id, template_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 3) lifecycle_transitions（流转/动作定义）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lifecycle_transitions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    template_id      UUID NOT NULL,              -- -> lifecycle_templates.id（逻辑外键）
    action_code      VARCHAR(64) NOT NULL,
    action_name      VARCHAR(128) NOT NULL,
    from_state       VARCHAR(64) NOT NULL,
    to_state         VARCHAR(64) NOT NULL,
    require_approval BOOLEAN NOT NULL DEFAULT FALSE,
    require_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    guard_rule       JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order       INT NOT NULL DEFAULT 0,
    description      TEXT,
    created_by       UUID,
    updated_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_lifecycle_transitions_tenant_template_action_from_active
    ON lifecycle_transitions (tenant_id, template_id, action_code, from_state) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_lifecycle_transitions_tenant_template
    ON lifecycle_transitions (tenant_id, template_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 4) lifecycle_events（生命周期事件，只追加）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lifecycle_events (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    asset_id           UUID NOT NULL,               -- -> assets.id（逻辑外键）
    template_id        UUID NOT NULL,               -- 触发时所用模板
    transition_id      UUID,                        -- 触发时所用 transition（审批分支可空）
    action_code        VARCHAR(64) NOT NULL,
    action_name        VARCHAR(128) NOT NULL,
    from_state         VARCHAR(64) NOT NULL,
    to_state           VARCHAR(64) NOT NULL,
    operator_id        UUID NOT NULL,               -- 操作人（来自 JWT principal.userId）
    operator_name      VARCHAR(128),                -- 操作人显示名（来自 JWT displayName）
    reason             TEXT,
    form_data          JSONB NOT NULL DEFAULT '{}'::jsonb,
    attachment_ids     JSONB NOT NULL DEFAULT '[]'::jsonb,
    approval_instance_id UUID,                      -- 关联审批实例（MVP-2 恒 null）
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted            BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_lifecycle_events_tenant_asset
    ON lifecycle_events (tenant_id, asset_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_lifecycle_events_tenant_asset_created
    ON lifecycle_events (tenant_id, asset_id, created_at DESC) WHERE deleted = FALSE;
