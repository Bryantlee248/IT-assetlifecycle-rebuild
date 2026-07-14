-- =============================================================================
-- MVP-1 元数据与资产核心：种子数据（V4）
-- 复用 V2 的 demo 租户(b2222222-...) 与 tenant_admin 角色(d4444444-...)。
-- 内容：
--   1) 新增权限目录码 metadata:manage / asset:view / asset:create / asset:update / asset:delete
--   2) 新增 asset_admin / auditor 角色并绑定权限（tenant_admin 与 asset_admin 均获 metadata:manage + asset:*）
--   3) 4 类资产类型（server/network_device/security_device/software_license）
--   4) 每类字段定义（含 server/network/security 的 serial_no 唯一=tenant；software_license 的 license_key 敏感+加密+last4）
--   5) 每类 form/list/search schema
--   6) 演示位置树：总部机房 -> A01 -> A01-10U
-- 注意：field_permission_rules 表本阶段不喂数据（由内存默认矩阵实现）。
-- =============================================================================

-- 常量（仅阅读）
-- DEMO_TENANT_ID      = b2222222-2222-2222-2222-222222222222
-- TENANT_ADMIN_ROLE   = d4444444-4444-4444-4444-444444444444
-- ASSET_ADMIN_ROLE    = f6666666-6666-6666-6666-666666666666
-- AUDITOR_ROLE        = f7777777-7777-7777-7777-777777777777
-- ASSET_ADMIN_USER    = f8888888-8888-8888-8888-888888888888
-- AUDITOR_USER        = f9999999-9999-9999-9999-999999999999
-- PLATFORM_ADMIN_ID   = a1111111-1111-1111-1111-111111111111
-- SERVER_TYPE         = 11111111-1111-1111-1111-111111111101
-- NETWORK_TYPE        = 11111111-1111-1111-1111-111111111102
-- SECURITY_TYPE       = 11111111-1111-1111-1111-111111111103
-- LICENSE_TYPE        = 11111111-1111-1111-1111-111111111104

-- -----------------------------------------------------------------------------
-- 1) 权限目录（新增 5 个 MVP-1 功能权限码）
-- -----------------------------------------------------------------------------
INSERT INTO permission (created_at, updated_at, created_by, updated_by, deleted, code, name, module, description) VALUES
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'metadata:manage', '元数据管理',     'metadata', '资产类型/字段/表单/列表/查询配置'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'asset:view',     '资产-查看',       'asset',    '查看资产台账'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'asset:create',   '资产-创建',       'asset',    '新增资产'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'asset:update',   '资产-编辑',       'asset',    '编辑资产'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'asset:delete',   '资产-删除',       'asset',    '删除资产')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2) 新增角色：asset_admin / auditor（tenant_admin 复用 V2 的 d4444444）
-- -----------------------------------------------------------------------------
INSERT INTO role (id, created_at, updated_at, created_by, updated_by, deleted, tenant_id, code, name, description, is_system)
VALUES (
    'f6666666-6666-6666-6666-666666666666',
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'b2222222-2222-2222-2222-222222222222',
    'asset_admin', '资产管理员', '内置资产管理员，负责资产台账全量', true
), (
    'f7777777-7777-7777-7777-777777777777',
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'b2222222-2222-2222-2222-222222222222',
    'auditor', '审计员', '内置审计员，只读且敏感字段脱敏', true
)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3) 角色-权限绑定：tenant_admin 与 asset_admin 均获 metadata:manage + asset:*
-- -----------------------------------------------------------------------------
INSERT INTO role_permission (created_at, updated_at, created_by, updated_by, deleted, tenant_id, role_id, permission_code)
SELECT now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false,
       'b2222222-2222-2222-2222-222222222222', v.role_id, v.code
FROM (VALUES
    ('d4444444-4444-4444-4444-444444444444', 'metadata:manage'),
    ('d4444444-4444-4444-4444-444444444444', 'asset:view'),
    ('d4444444-4444-4444-4444-444444444444', 'asset:create'),
    ('d4444444-4444-4444-4444-444444444444', 'asset:update'),
    ('d4444444-4444-4444-4444-444444444444', 'asset:delete'),
    ('f6666666-6666-6666-6666-666666666666', 'metadata:manage'),
    ('f6666666-6666-6666-6666-666666666666', 'asset:view'),
    ('f6666666-6666-6666-6666-666666666666', 'asset:create'),
    ('f6666666-6666-6666-6666-666666666666', 'asset:update'),
    ('f6666666-6666-6666-6666-666666666666', 'asset:delete')
) AS v(role_id, code)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4) 演示用户：asset_admin / auditor（密码 Tenant@123，与 tenant_admin 同哈希）
-- -----------------------------------------------------------------------------
INSERT INTO platform_user (id, created_at, updated_at, created_by, updated_by, deleted, username, password_hash, display_name, email, phone, status, must_change_password, is_platform_admin)
VALUES (
    'f8888888-8888-8888-8888-888888888888',
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'asset_admin', '$2b$10$SDt5rjYPvmg4v.TlXOuhVOc9jUd18BpLGAHIS.0eg/jovuOq0uBSy',
    '演示资产管理员', 'asset_admin@itam.local', null, 'ACTIVE', true, false
), (
    'f9999999-9999-9999-9999-999999999999',
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'auditor', '$2b$10$SDt5rjYPvmg4v.TlXOuhVOc9jUd18BpLGAHIS.0eg/jovuOq0uBSy',
    '演示审计员', 'auditor@itam.local', null, 'ACTIVE', true, false
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_user (created_at, updated_at, created_by, updated_by, deleted, tenant_id, platform_user_id, status, role_id)
VALUES (
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'b2222222-2222-2222-2222-222222222222', 'f8888888-8888-8888-8888-888888888888', 'ACTIVE', 'f6666666-6666-6666-6666-666666666666'
), (
    now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
    false, 'b2222222-2222-2222-2222-222222222222', 'f9999999-9999-9999-9999-999999999999', 'ACTIVE', 'f7777777-7777-7777-7777-777777777777'
)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5) 4 类资产类型
-- -----------------------------------------------------------------------------
INSERT INTO asset_types (id, tenant_id, parent_id, type_code, type_name, asset_kind, lifecycle_template_id, icon, enabled, sort_order, created_by, updated_by, created_at, updated_at, deleted)
VALUES (
    '11111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', null,
    'server', '服务器', 'tangible', null, 'CPU', true, 1,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
), (
    '11111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', null,
    'network_device', '网络设备', 'tangible', null, 'Connection', true, 2,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
), (
    '11111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', null,
    'security_device', '安全设备', 'tangible', null, 'Lock', true, 3,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
), (
    '11111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', null,
    'software_license', '软件许可证', 'intangible', null, 'Key', true, 4,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6) 字段定义
--    热点物理列（brand/model/serial_no/warranty_end_date/vendor/license_end_date）storage_type=physical
--    serial_no 唯一范围 = tenant（命中 ux_assets_tenant_serial_no_active 兜底）
--    software_license.license_key：sensitive + encrypted + maskRule=last4
-- -----------------------------------------------------------------------------
INSERT INTO field_definitions (id, tenant_id, asset_type_id, field_code, field_name, field_type, storage_type, physical_column, required, unique_scope, default_value, validation_rule, data_source, searchable, sortable, indexed, visible, editable, sensitive, encrypted, mask_rule, sort_order, status, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    -- ===== server =====
    ('21111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'brand', '品牌', 'text', 'physical', 'brand', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 1, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'model', '型号', 'text', 'physical', 'model', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 2, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'serial_no', '序列号', 'text', 'physical', 'serial_no', false, 'tenant', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 3, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'memory_gb', '内存(GB)', 'number', 'jsonb', NULL, false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 4, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111105', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'cpu_spec', 'CPU规格', 'textarea', 'jsonb', NULL, false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 5, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111106', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101', 'warranty_end_date', '保修到期日', 'date', 'physical', 'warranty_end_date', false, 'none', NULL, NULL, NULL, true, true, false, true, true, false, false, NULL, 6, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    -- ===== network_device =====
    ('21111111-1111-1111-1111-111111111201', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102', 'brand', '品牌', 'text', 'physical', 'brand', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 1, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111202', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102', 'model', '型号', 'text', 'physical', 'model', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 2, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111203', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102', 'serial_no', '序列号', 'text', 'physical', 'serial_no', false, 'tenant', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 3, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111204', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102', 'management_ip', '管理IP', 'text', 'jsonb', NULL, false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 4, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111205', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102', 'warranty_end_date', '保修到期日', 'date', 'physical', 'warranty_end_date', false, 'none', NULL, NULL, NULL, true, true, false, true, true, false, false, NULL, 5, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    -- ===== security_device =====
    ('21111111-1111-1111-1111-111111111301', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103', 'brand', '品牌', 'text', 'physical', 'brand', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 1, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111302', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103', 'model', '型号', 'text', 'physical', 'model', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 2, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111303', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103', 'serial_no', '序列号', 'text', 'physical', 'serial_no', false, 'tenant', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 3, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111304', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103', 'security_zone', '安全域', 'text', 'jsonb', NULL, false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 4, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111305', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103', 'warranty_end_date', '保修到期日', 'date', 'physical', 'warranty_end_date', false, 'none', NULL, NULL, NULL, true, true, false, true, true, false, false, NULL, 5, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    -- ===== software_license =====
    ('21111111-1111-1111-1111-111111111401', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104', 'vendor', '厂商', 'text', 'physical', 'vendor', false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 1, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111402', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104', 'license_model', '授权模式', 'enum', 'jsonb', NULL, false, 'none', NULL, '{"enum":["subscription","perpetual","byol"]}'::jsonb, NULL, true, false, false, true, true, false, false, NULL, 2, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111403', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104', 'total_quantity', '授权总数', 'number', 'jsonb', NULL, false, 'none', NULL, NULL, NULL, true, false, false, true, true, false, false, NULL, 3, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111404', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104', 'license_end_date', '到期日', 'date', 'physical', 'license_end_date', false, 'none', NULL, NULL, NULL, true, true, false, true, true, false, false, NULL, 4, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('21111111-1111-1111-1111-111111111405', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104', 'license_key', '授权密钥', 'text', 'encrypted', NULL, false, 'none', NULL, NULL, NULL, false, false, false, true, false, true, true, 'last4', 5, 'enabled', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7) 表单/列表/查询 schema（每类一套）
--    form_schema：分组 + 字段顺序；list_view：列；search_schema：筛选条件
-- -----------------------------------------------------------------------------
INSERT INTO form_schemas (id, tenant_id, asset_type_id, schema_json, version, enabled, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('31111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101',
     '{"groups":[{"title":"基础信息","columns":2,"fields":["asset_no","asset_name","brand","model","serial_no","owner_org_id","responsible_user_id","location_id"]},{"title":"配置","columns":2,"fields":["memory_gb","cpu_spec","warranty_end_date"]}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('31111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102',
     '{"groups":[{"title":"基础信息","columns":2,"fields":["asset_no","asset_name","brand","model","serial_no","owner_org_id","responsible_user_id","location_id"]},{"title":"配置","columns":2,"fields":["management_ip","warranty_end_date"]}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('31111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103',
     '{"groups":[{"title":"基础信息","columns":2,"fields":["asset_no","asset_name","brand","model","serial_no","owner_org_id","responsible_user_id","location_id"]},{"title":"配置","columns":2,"fields":["security_zone","warranty_end_date"]}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('31111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104',
     '{"groups":[{"title":"基础信息","columns":2,"fields":["asset_no","asset_name","vendor","owner_org_id","responsible_user_id"]},{"title":"授权","columns":2,"fields":["license_model","total_quantity","license_end_date","license_key"]}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO list_view_schemas (id, tenant_id, asset_type_id, schema_json, version, enabled, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('32111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101',
     '{"columns":[{"field":"asset_no","title":"资产编号","width":140},{"field":"asset_name","title":"名称","width":180},{"field":"brand","title":"品牌","width":120},{"field":"model","title":"型号","width":120},{"field":"serial_no","title":"序列号","width":140},{"field":"lifecycle_status","title":"状态","width":100},{"field":"responsible_user_id","title":"责任人","width":120},{"field":"updated_at","title":"更新时间","width":160}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('32111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102',
     '{"columns":[{"field":"asset_no","title":"资产编号","width":140},{"field":"asset_name","title":"名称","width":180},{"field":"brand","title":"品牌","width":120},{"field":"model","title":"型号","width":120},{"field":"serial_no","title":"序列号","width":140},{"field":"lifecycle_status","title":"状态","width":100}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('32111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103',
     '{"columns":[{"field":"asset_no","title":"资产编号","width":140},{"field":"asset_name","title":"名称","width":180},{"field":"brand","title":"品牌","width":120},{"field":"model","title":"型号","width":120},{"field":"serial_no","title":"序列号","width":140},{"field":"lifecycle_status","title":"状态","width":100}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('32111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104',
     '{"columns":[{"field":"asset_no","title":"资产编号","width":140},{"field":"asset_name","title":"名称","width":180},{"field":"vendor","title":"厂商","width":140},{"field":"license_model","title":"授权模式","width":120},{"field":"total_quantity","title":"授权总数","width":100},{"field":"license_end_date","title":"到期日","width":120},{"field":"lifecycle_status","title":"状态","width":100}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO search_schemas (id, tenant_id, asset_type_id, schema_json, version, enabled, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('33111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111101',
     '{"filters":[{"field":"keyword","control":"keyword","label":"关键字"},{"field":"status","control":"status","label":"状态"},{"field":"brand","control":"text","label":"品牌"},{"field":"owner_org_id","control":"org","label":"组织"},{"field":"location_id","control":"location","label":"位置"},{"field":"warranty_end_date","control":"date_range","label":"保修到期"}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('33111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111102',
     '{"filters":[{"field":"keyword","control":"keyword","label":"关键字"},{"field":"status","control":"status","label":"状态"},{"field":"brand","control":"text","label":"品牌"},{"field":"location_id","control":"location","label":"位置"}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('33111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111103',
     '{"filters":[{"field":"keyword","control":"keyword","label":"关键字"},{"field":"status","control":"status","label":"状态"},{"field":"brand","control":"text","label":"品牌"},{"field":"location_id","control":"location","label":"位置"}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('33111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111104',
     '{"filters":[{"field":"keyword","control":"keyword","label":"关键字"},{"field":"status","control":"status","label":"状态"},{"field":"vendor","control":"text","label":"厂商"},{"field":"license_model","control":"enum","label":"授权模式"},{"field":"license_end_date","control":"date_range","label":"到期日"}]}'::jsonb,
     1, true, 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 8) 演示位置树：总部机房 -> A01 -> A01-10U
-- -----------------------------------------------------------------------------
INSERT INTO locations (id, tenant_id, parent_id, name, code, path, sort_order, created_by, updated_by, created_at, updated_at, deleted)
VALUES (
    '34111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', null,
    '总部机房', 'HQ-ROOM', '/34111111-1111-1111-1111-111111111101', 1,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
), (
    '34111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '34111111-1111-1111-1111-111111111101',
    'A01', 'A01', '/34111111-1111-1111-1111-111111111101/34111111-1111-1111-1111-111111111102', 1,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
), (
    '34111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '34111111-1111-1111-1111-111111111102',
    'A01-10U', 'A01-10U', '/34111111-1111-1111-1111-111111111101/34111111-1111-1111-1111-111111111102/34111111-1111-1111-1111-111111111103', 1,
    'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false
)
ON CONFLICT (id) DO NOTHING;
