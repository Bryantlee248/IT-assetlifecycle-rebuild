-- =============================================================================
-- MVP-3 审批 · 通知 · 状态权限：种子数据（V9）
-- 复用 V2 的 demo 租户(b2222222-...) 与 tenant_admin 角色(d4444444-...)，
--     以及 V2 平台管理员(a1111111-...) 作为 created_by/updated_by。
--
-- 内容：
--   1) 5 个权限码（approval:view/approve/reject、notification:view/read，module 对应）
--      + 绑定 tenant_admin。lifecycle:transition 已在 V7 注册，跳过（F2/Q6）。
--   2) 4 个默认审批模板（单节点，ROLE -> tenant_admin）+ 4 个 approval_nodes。
--   3) 关联 5 个需审批过渡（tangible/intangible 的 submit_purchase / retire /
--      assign_license / renew_license）。
--   4) 菜单：无 menu 表（F10），前端以路由为入口，后端 AuthService.menu() 补登
--      审批/通知节点，此处不插菜单行。
--
-- 幂等：INSERT 用 ON CONFLICT DO NOTHING；UPDATE 用 approval_template_id IS NULL 条件；
--      不碰 V1~V7 行。
--
-- UUID 方案（便于验收交叉引用）：
--   模板    f1111111-...-101/102/103/104
--   节点    f2111111-...-101/102/103/104
-- =============================================================================

-- 常量（仅阅读）
-- DEMO_TENANT_ID    = b2222222-2222-2222-2222-222222222222
-- TENANT_ADMIN_ROLE = d4444444-4444-4444-4444-444444444444
-- PLATFORM_ADMIN_ID = a1111111-1111-1111-1111-111111111111

-- -----------------------------------------------------------------------------
-- 1) 权限码（5 个）+ 绑定 tenant_admin
--    lifecycle:transition 已在 V7 注册并绑定 tenant_admin，按 Q6 跳过。
-- -----------------------------------------------------------------------------
INSERT INTO permission (created_at, updated_at, created_by, updated_by, deleted, code, name, module, description)
VALUES
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'approval:view',     '审批-查看',   'approval',     '查看审批待办与审批实例'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'approval:approve',  '审批-通过',   'approval',     '通过审批任务'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'approval:reject',   '审批-驳回',   'approval',     '驳回审批任务'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'notification:view', '通知-查看',   'notification', '查看站内通知'),
    (now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false, 'notification:read', '通知-已读',   'notification', '标记通知为已读')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (created_at, updated_at, created_by, updated_by, deleted, tenant_id, role_id, permission_code)
SELECT now(), now(), 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', false,
       'b2222222-2222-2222-2222-222222222222', 'd4444444-4444-4444-4444-444444444444', v.code
FROM (VALUES ('approval:view'), ('approval:approve'), ('approval:reject'),
             ('notification:view'), ('notification:read')) AS v(code)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2) 4 个默认审批模板（单节点）+ 4 个 approval_nodes（ROLE -> tenant_admin）
--    共用模板：ATP-SUBMIT-PURCHASE 同时服务 tangible & intangible 的 submit_purchase。
-- -----------------------------------------------------------------------------
INSERT INTO approval_templates (id, tenant_id, name, asset_kind, action_code, description, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('f1111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', '提交采购审批',     NULL,      'submit_purchase', '资产提交采购需审批（tangible & intangible 共用）', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f1111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', '退役审批',         NULL,      'retire',          '资产退役需审批', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f1111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', '分配授权审批',     NULL,      'assign_license',  '软件许可证分配授权需审批', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f1111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', '续费审批',         NULL,      'renew_license',   '软件许可证续费需审批', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_nodes (id, tenant_id, template_id, node_order, approver_type, approver_user_id, approver_role_id, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('f2111111-1111-1111-1111-111111111101', 'b2222222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111101', 1, 'ROLE', NULL, 'd4444444-4444-4444-4444-444444444444', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f2111111-1111-1111-1111-111111111102', 'b2222222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111102', 1, 'ROLE', NULL, 'd4444444-4444-4444-4444-444444444444', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f2111111-1111-1111-1111-111111111103', 'b2222222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111103', 1, 'ROLE', NULL, 'd4444444-4444-4444-4444-444444444444', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false),
    ('f2111111-1111-1111-1111-111111111104', 'b2222222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111104', 1, 'ROLE', NULL, 'd4444444-4444-4444-4444-444444444444', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3) 关联 5 个需审批过渡
--    仅当 approval_template_id IS NULL 时才更新，保证幂等、不覆盖既有配置。
-- -----------------------------------------------------------------------------
UPDATE lifecycle_transitions
   SET require_approval = true,
       approval_template_id = 'f1111111-1111-1111-1111-111111111101'
 WHERE id = 'e1111111-1111-1111-1111-111111111101'
   AND tenant_id = 'b2222222-2222-2222-2222-222222222222'
   AND approval_template_id IS NULL;

UPDATE lifecycle_transitions
   SET require_approval = true,
       approval_template_id = 'f1111111-1111-1111-1111-111111111102'
 WHERE id = 'e1111111-1111-1111-1111-111111111108'
   AND tenant_id = 'b2222222-2222-2222-2222-222222222222'
   AND approval_template_id IS NULL;

UPDATE lifecycle_transitions
   SET require_approval = true,
       approval_template_id = 'f1111111-1111-1111-1111-111111111101'
 WHERE id = 'e2222222-2222-2222-2222-222222222201'
   AND tenant_id = 'b2222222-2222-2222-2222-222222222222'
   AND approval_template_id IS NULL;

UPDATE lifecycle_transitions
   SET require_approval = true,
       approval_template_id = 'f1111111-1111-1111-1111-111111111103'
 WHERE id = 'e2222222-2222-2222-2222-222222222203'
   AND tenant_id = 'b2222222-2222-2222-2222-222222222222'
   AND approval_template_id IS NULL;

UPDATE lifecycle_transitions
   SET require_approval = true,
       approval_template_id = 'f1111111-1111-1111-1111-111111111104'
 WHERE id = 'e2222222-2222-2222-2222-222222222206'
   AND tenant_id = 'b2222222-2222-2222-2222-222222222222'
   AND approval_template_id IS NULL;
