// 权限码目录（取自后端 @PreAuthorize 注解与权限表）。
// 后端未提供"列出全部权限码"的接口，角色权限分配页据此作为多选候选项。
// 实际保存时由后端校验合法性（仅保存目录中存在的码）。

export interface PermissionMeta {
  code: string
  label: string
  group: string
}

export const PERMISSION_CATALOG: PermissionMeta[] = [
  { code: 'tenant:list', label: '租户-查看', group: '平台租户' },
  { code: 'tenant:create', label: '租户-创建', group: '平台租户' },
  { code: 'tenant:enable', label: '租户-启用', group: '平台租户' },
  { code: 'tenant:disable', label: '租户-停用', group: '平台租户' },

  { code: 'org:list', label: '组织-查看', group: '组织管理' },
  { code: 'org:create', label: '组织-创建', group: '组织管理' },
  { code: 'org:update', label: '组织-编辑', group: '组织管理' },
  { code: 'org:delete', label: '组织-删除', group: '组织管理' },

  { code: 'user:list', label: '用户-查看', group: '用户管理' },
  { code: 'user:create', label: '用户-创建', group: '用户管理' },
  { code: 'user:update', label: '用户-编辑', group: '用户管理' },
  { code: 'user:delete', label: '用户-删除', group: '用户管理' },

  { code: 'role:list', label: '角色-查看', group: '角色权限' },
  { code: 'role:create', label: '角色-创建', group: '角色权限' },
  { code: 'role:update', label: '角色-编辑', group: '角色权限' },
  { code: 'role:delete', label: '角色-删除', group: '角色权限' },
  { code: 'role:assign', label: '角色-分配权限', group: '角色权限' },

  { code: 'menu:view', label: '菜单-可见', group: '通用' },
  { code: 'profile:view', label: '个人中心-查看', group: '通用' },
  { code: 'profile:update', label: '个人中心-修改', group: '通用' },

  { code: 'metadata:manage', label: '元数据-管理', group: '元数据配置' },
  { code: 'asset:view', label: '资产-查看', group: '资产管理' },
  { code: 'asset:create', label: '资产-创建', group: '资产管理' },
  { code: 'asset:update', label: '资产-编辑', group: '资产管理' },
  { code: 'asset:delete', label: '资产-删除', group: '资产管理' },

  { code: 'approval:view', label: '审批-查看', group: '审批管理' },
  { code: 'approval:approve', label: '审批-通过', group: '审批管理' },
  { code: 'approval:reject', label: '审批-驳回', group: '审批管理' },
  { code: 'notification:view', label: '通知-查看', group: '通知中心' },
  { code: 'notification:read', label: '通知-已读', group: '通知中心' }
]

export const PERMISSION_GROUPS: string[] = Array.from(
  new Set(PERMISSION_CATALOG.map((p) => p.group))
)
