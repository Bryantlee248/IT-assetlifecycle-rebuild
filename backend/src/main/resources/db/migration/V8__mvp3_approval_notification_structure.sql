-- =============================================================================
-- MVP-3 审批 · 通知 · 状态权限：结构迁移（V8）
-- 接续 V7（MVP-2 生命周期）。绝不改动 V1~V7。
--
-- 内容：
--   1) lifecycle_transitions 增加 approval_template_id（关联审批模板）
--   2) 新建 6 张表（均继承 TenantEntity 公共列 + 软删除 + 部分唯一/查询索引）：
--        approval_templates / approval_nodes / approval_instances / approval_tasks
--        notifications / state_permission_rules
--
-- 约定（与 V1~V7 一致）：
--   主键 uuid default gen_random_uuid()；租户表带 tenant_id；软删除 deleted；
--   IF NOT EXISTS / 部分唯一索引 WHERE deleted = false 保证幂等可重复执行；
--   不建物理外键（仅应用层校验同租户）。
--   注意：server.servlet.context-path=/api 与 SQL 无关。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) lifecycle_transitions 扩展：关联审批模板
-- -----------------------------------------------------------------------------
ALTER TABLE lifecycle_transitions
    ADD COLUMN IF NOT EXISTS approval_template_id UUID;

-- -----------------------------------------------------------------------------
-- 2.1 approval_templates（审批模板，单节点默认）
--     asset_kind 为空表示全大类适用；action_code 为模板所服务的动作。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_templates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(128) NOT NULL,
    asset_kind    VARCHAR(32),
    action_code   VARCHAR(64),
    description   TEXT,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_approval_templates_tenant_name_active
    ON approval_templates (tenant_id, name) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2.2 approval_nodes（审批节点；单节点默认 1 行，node_order=1）
--     approver_type ∈ {USER, ROLE}；USER -> approver_user_id；ROLE -> approver_role_id。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_nodes (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    template_id      UUID NOT NULL,
    node_order       INT NOT NULL DEFAULT 1,
    approver_type    VARCHAR(16) NOT NULL,
    approver_user_id UUID,
    approver_role_id UUID,
    created_by       UUID,
    updated_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_approval_nodes_tenant_template
    ON approval_nodes (tenant_id, template_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2.3 approval_instances（审批实例：一次生命周期动作审批的聚合根）
--     status ∈ {pending, approved, rejected, cancelled}
--     current_node_order：当前所处节点（多节点顺序审批支持）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_instances (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    template_id     UUID NOT NULL,
    asset_id        UUID NOT NULL,
    transition_id   UUID,
    action_code     VARCHAR(64) NOT NULL,
    action_name     VARCHAR(128),
    from_state      VARCHAR(64) NOT NULL,
    to_state        VARCHAR(64) NOT NULL,
    applicant_id    UUID NOT NULL,
    applicant_name  VARCHAR(128),
    reason          TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    current_node_order INT NOT NULL DEFAULT 1,
    title           VARCHAR(256),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_approval_instances_tenant_asset
    ON approval_instances (tenant_id, asset_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_approval_instances_tenant_status
    ON approval_instances (tenant_id, status) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2.4 approval_tasks（审批任务）
--     approver_id 为具体平台用户 -> "非审批人不能审批"的硬保证。
--     status ∈ {pending, approved, rejected, cancelled}
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_tasks (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    instance_id  UUID NOT NULL,
    node_order   INT NOT NULL DEFAULT 1,
    approver_id  UUID NOT NULL,
    approver_type VARCHAR(16) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'pending',
    comment      TEXT,
    decided_by   UUID,
    decided_at   TIMESTAMPTZ,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_approval_tasks_tenant_approver
    ON approval_tasks (tenant_id, approver_id, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_approval_tasks_tenant_instance
    ON approval_tasks (tenant_id, instance_id) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2.5 notifications（站内通知；双隔离：tenant_id + receiver_id）
--     type ∈ {APPROVAL_TASK, APPROVAL_APPROVED, APPROVAL_REJECTED, APPROVAL_FORWARDED}
--     read_at 非空即已读。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    receiver_id   UUID NOT NULL,
    type          VARCHAR(32) NOT NULL,
    business_type VARCHAR(64),
    business_id   UUID,
    title         VARCHAR(256) NOT NULL,
    content       TEXT,
    read_at       TIMESTAMPTZ,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_receiver_read
    ON notifications (tenant_id, receiver_id, read_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_receiver_created
    ON notifications (tenant_id, receiver_id, created_at DESC) WHERE deleted = FALSE;

-- -----------------------------------------------------------------------------
-- 2.6 state_permission_rules（状态权限规则）
--     asset_type_id 为空表示全类型生效；allowed_actions 为动作码 JSONB 数组。
--     命中条件 = role_id 且（asset_type_id 为空 或 等于资产类型）且 lifecycle_state 等于当前状态。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS state_permission_rules (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    role_id        UUID NOT NULL,
    asset_type_id  UUID,
    lifecycle_state VARCHAR(64) NOT NULL,
    allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    description    TEXT,
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_state_permission_rules_tenant
    ON state_permission_rules (tenant_id) WHERE deleted = FALSE;
