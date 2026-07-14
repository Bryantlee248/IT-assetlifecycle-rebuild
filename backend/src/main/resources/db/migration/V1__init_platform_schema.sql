-- =============================================================================
-- MVP-0 平台底座：基础表结构（V1）
-- 数据库：PostgreSQL 13+
-- 约定：
--   * 主键 id uuid default gen_random_uuid() primary key
--   * 平台级表无 tenant_id；租户级表 tenant_id uuid not null
--   * 所有表含 created_at/updated_at timestamptz、created_by/updated_by uuid、
--     deleted boolean not null default false（软删除）
--   * 唯一约束统一使用部分唯一索引 ... WHERE deleted = false
--   * 审计日志(不可变)无 updated_*/deleted
-- 说明：audit_log.actor_id 不建外键（匿名登录失败使用哨兵 UUID，不存在于
--       platform_user）；role_permission.permission_code 不建外键（permission.code
--       为部分唯一索引，PostgreSQL 不允许 FK 引用部分唯一索引）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 平台用户（平台级，无 tenant_id）
-- ---------------------------------------------------------------------------
CREATE TABLE platform_user (
    id                uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    deleted           boolean     NOT NULL DEFAULT false,
    username          varchar(64) NOT NULL,
    password_hash     varchar(255) NOT NULL,
    display_name      varchar(128),
    email             varchar(255),
    phone             varchar(64),
    status            varchar(16) NOT NULL DEFAULT 'ACTIVE',
    must_change_password boolean  NOT NULL DEFAULT false,
    is_platform_admin boolean     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX uq_platform_user_username ON platform_user (username) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 租户（平台级）
-- ---------------------------------------------------------------------------
CREATE TABLE tenant (
    id          uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted     boolean     NOT NULL DEFAULT false,
    name        varchar(128) NOT NULL,
    code        varchar(64)  NOT NULL,
    status      varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    description varchar(512)
);
CREATE UNIQUE INDEX uq_tenant_code ON tenant (code) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 租户用户关联（租户级）
-- ---------------------------------------------------------------------------
CREATE TABLE tenant_user (
    id               uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    deleted          boolean     NOT NULL DEFAULT false,
    tenant_id        uuid        NOT NULL,
    platform_user_id uuid        NOT NULL,
    status           varchar(16) NOT NULL DEFAULT 'ACTIVE',
    role_id          uuid,
    CONSTRAINT fk_tenant_user_tenant         FOREIGN KEY (tenant_id)        REFERENCES tenant (id),
    CONSTRAINT fk_tenant_user_platform_user  FOREIGN KEY (platform_user_id) REFERENCES platform_user (id)
);
CREATE UNIQUE INDEX uq_tenant_user_tenant_platform ON tenant_user (tenant_id, platform_user_id) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 组织（租户级，树形）
-- ---------------------------------------------------------------------------
CREATE TABLE organization (
    id         uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted    boolean     NOT NULL DEFAULT false,
    tenant_id  uuid        NOT NULL,
    parent_id  uuid,
    name       varchar(128) NOT NULL,
    code       varchar(64)  NOT NULL,
    type       varchar(32),
    sort       int         NOT NULL DEFAULT 0,
    status     varchar(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_organization_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_id) REFERENCES organization (id)
);
CREATE UNIQUE INDEX uq_organization_tenant_code ON organization (tenant_id, code) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 角色（租户级）
-- ---------------------------------------------------------------------------
CREATE TABLE role (
    id          uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted     boolean     NOT NULL DEFAULT false,
    tenant_id   uuid        NOT NULL,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    description varchar(512),
    is_system   boolean     NOT NULL DEFAULT false,
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
CREATE UNIQUE INDEX uq_role_tenant_code ON role (tenant_id, code) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 权限目录（平台级固定目录）
-- ---------------------------------------------------------------------------
CREATE TABLE permission (
    id          uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted     boolean     NOT NULL DEFAULT false,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    module      varchar(64),
    description varchar(512)
);
CREATE UNIQUE INDEX uq_permission_code ON permission (code) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 角色-权限关联（租户级）
-- 注意：permission_code 不建外键（见文件头说明）
-- ---------------------------------------------------------------------------
CREATE TABLE role_permission (
    id             uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    deleted        boolean     NOT NULL DEFAULT false,
    tenant_id      uuid        NOT NULL,
    role_id        uuid        NOT NULL,
    permission_code varchar(64) NOT NULL,
    CONSTRAINT fk_role_permission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_role_permission_role   FOREIGN KEY (role_id)   REFERENCES role (id)
);
CREATE UNIQUE INDEX uq_role_permission_unique ON role_permission (tenant_id, role_id, permission_code) WHERE deleted = false;

-- ---------------------------------------------------------------------------
-- 审计日志（平台级，不可变，无 updated_*/deleted）
-- 注意：actor_id 不建外键（见文件头说明）
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id         uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id  uuid,
    actor_id   uuid,
    actor_type varchar(16) NOT NULL,
    action     varchar(64) NOT NULL,
    biz_type   varchar(64),
    biz_id     varchar(64),
    detail     jsonb,
    ip         varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at);
CREATE INDEX idx_audit_log_actor_created  ON audit_log (actor_id, created_at);
