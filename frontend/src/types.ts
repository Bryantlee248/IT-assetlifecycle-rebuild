// 与后端 DTO 严格对应的 TS 类型定义。
// 后端所有响应体统一为 { code, message, data, traceId }，code===0 表示成功。
// UUID 字段在 JSON 中以字符串形式传输。

/** 统一响应信封 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 分页结果：data 内嵌 { page, size, total, list } */
export interface PageResult<T> {
  page: number
  size: number
  total: number
  list: T[]
}

export type UserType = 'PLATFORM' | 'TENANT'

// ===== 认证相关 =====

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userType: UserType
  mustChangePassword: boolean
  tenantId: string | null
  username: string
  displayName: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface UserInfoResponse {
  id: string
  username: string
  displayName: string
  email: string | null
  phone: string | null
  userType: UserType
  tenantId: string | null
  mustChangePassword: boolean
  permissions: string[]
}

export interface MenuNode {
  key: string
  title: string
  path: string
  icon: string
  children: MenuNode[]
}

export interface TenantBrief {
  id: string
  name: string
  code: string
  status: string
}

// ===== 平台租户管理 =====

export interface CreateTenantRequest {
  name: string
  code: string
  description?: string
}

export interface TenantResponse {
  id: string
  name: string
  code: string
  status: string
  description: string | null
  createdAt: string
}

// ===== 组织管理 =====

export interface CreateOrgRequest {
  parentId: string | null
  name: string
  code: string
  type?: string
  sort: number
}

export interface UpdateOrgRequest {
  parentId?: string | null
  name?: string
  code?: string
  type?: string
  sort?: number
  status?: string
}

export interface OrgNode {
  id: string
  name: string
  code: string
  type: string | null
  sort: number
  status: string
  children: OrgNode[]
}

// ===== 用户管理 =====

export interface CreateUserRequest {
  username: string
  password: string
  displayName?: string
  email?: string
  phone?: string
  roleId?: string | null
  status?: string
}

export interface UpdateUserRequest {
  displayName?: string
  email?: string
  phone?: string
  roleId?: string | null
  status?: string
}

export interface UserResponse {
  id: string
  username: string
  displayName: string | null
  email: string | null
  phone: string | null
  status: string
  roleId: string | null
  roleName: string | null
  tenantId: string
}

// ===== 角色权限 =====

export interface CreateRoleRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
}

export interface RoleResponse {
  id: string
  code: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
}

export interface RolePermissionResponse {
  roleId: string
  permissions: string[]
}

// ============================================================================
// MVP-1 元数据与资产核心
// ============================================================================

/** 资产大类（DEVICE/SOFTWARE/NETWORK/SECURITY…，后端以字符串透传） */
export type AssetKind = string

// ===== 资产类型 =====

export interface AssetTypeResponse {
  id: string
  tenantId: string
  parentId: string | null
  typeCode: string
  typeName: string
  assetKind: string
  lifecycleTemplateId: string | null
  icon: string | null
  enabled: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface AssetTypeNode {
  id: string
  typeCode: string
  typeName: string
  assetKind: string
  icon: string | null
  enabled: boolean
  sortOrder: number
  children: AssetTypeNode[]
}

export interface CreateAssetTypeRequest {
  parentId?: string | null
  typeCode: string
  typeName: string
  assetKind: string
  lifecycleTemplateId?: string | null
  icon?: string | null
  enabled?: boolean
  sortOrder?: number
}

export interface UpdateAssetTypeRequest {
  parentId?: string | null
  typeName?: string
  assetKind?: string
  lifecycleTemplateId?: string | null
  icon?: string | null
  enabled?: boolean
  sortOrder?: number
}

// ===== 字段定义 =====

export type FieldType =
  | 'string'
  | 'text'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'enum'
  | 'user'
  | 'org'
  | 'location'
  | 'asset'
  | string

export type StorageType = 'jsonb' | 'physical' | 'encrypted' | string
export type UniqueScope = 'none' | 'tenant' | 'asset_type' | string

export interface FieldDefinitionResponse {
  id: string
  tenantId: string
  assetTypeId: string
  fieldCode: string
  fieldName: string
  fieldType: string
  storageType: string
  physicalColumn: string | null
  required: boolean
  uniqueScope: string
  defaultValue: Record<string, unknown> | null
  validationRule: Record<string, unknown> | null
  dataSource: Record<string, unknown> | null
  searchable: boolean
  sortable: boolean
  indexed: boolean
  visible: boolean
  editable: boolean
  sensitive: boolean
  encrypted: boolean
  maskRule: string | null
  sortOrder: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface CreateFieldRequest {
  fieldCode: string
  fieldName: string
  fieldType: string
  storageType?: string
  physicalColumn?: string | null
  required?: boolean
  uniqueScope?: string
  defaultValue?: Record<string, unknown> | null
  validationRule?: Record<string, unknown> | null
  dataSource?: Record<string, unknown> | null
  searchable?: boolean
  sortable?: boolean
  indexed?: boolean
  visible?: boolean
  editable?: boolean
  sensitive?: boolean
  encrypted?: boolean
  maskRule?: string | null
  sortOrder?: number
  status?: string
}

export interface UpdateFieldRequest {
  fieldName?: string
  fieldType?: string
  storageType?: string
  physicalColumn?: string | null
  required?: boolean
  uniqueScope?: string
  defaultValue?: Record<string, unknown> | null
  validationRule?: Record<string, unknown> | null
  dataSource?: Record<string, unknown> | null
  searchable?: boolean
  sortable?: boolean
  indexed?: boolean
  visible?: boolean
  editable?: boolean
  sensitive?: boolean
  encrypted?: boolean
  maskRule?: string | null
  sortOrder?: number
  status?: string
}

// ===== 表单 / 列表 / 查询 配置 =====

export interface SchemaResponse {
  id: string
  tenantId: string
  assetTypeId: string
  schemaJson: Record<string, unknown>
  version: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}
export type FormSchemaResponse = SchemaResponse
export type ListViewResponse = SchemaResponse
export type SearchSchemaResponse = SchemaResponse

// ===== 字段权限视图（运行时聚合）=====

export interface FieldPermissionView {
  visible: boolean
  editable: boolean
  masked: boolean
  exportable: boolean
  maskRule: string | null
}

export interface RuntimeMetadataResponse {
  assetType: AssetTypeResponse
  fields: FieldDefinitionResponse[]
  fieldPermissions: Record<string, FieldPermissionView>
  formSchema: Record<string, unknown>
  listView: Record<string, unknown>
  searchSchema: Record<string, unknown>
}

// ===== 位置树 =====

export interface LocationNode {
  id: string
  name: string
  code: string
  path: string
  sortOrder: number
  children: LocationNode[]
}

// ===== 资产 =====

export interface AssetResponse {
  id: string
  assetNo: string
  assetName: string
  assetKind: string
  assetTypeId: string
  assetTypeName: string | null
  lifecycleStatus: string
  status: string
  sourceType: string
  ownerUserId: string | null
  ownerOrgId: string | null
  locationId: string | null
  costCenterId: string | null
  responsibleUserId: string | null
  createdAt: string
  updatedAt: string
  fields: Record<string, unknown>
}

export interface AssetListItem {
  id: string
  assetNo: string
  assetName: string
  assetTypeId: string
  assetTypeName: string | null
  lifecycleStatus: string
  status: string
  fields: Record<string, unknown>
}

export interface AssetQuery {
  assetTypeId?: string
  lifecycleStatus?: string
  keyword?: string
  locationId?: string
  ownerUserId?: string
  ownerOrgId?: string
  responsibleUserId?: string
  warrantyEndFrom?: string
  warrantyEndTo?: string
  licenseEndFrom?: string
  licenseEndTo?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateAssetRequest {
  assetTypeId: string
  assetName: string
  assetNo: string
  ownerUserId?: string | null
  ownerOrgId?: string | null
  locationId?: string | null
  costCenterId?: string | null
  responsibleUserId?: string | null
  serialNo?: string | null
  brand?: string | null
  model?: string | null
  vendor?: string | null
  warrantyEndDate?: string | null
  licenseEndDate?: string | null
  lifecycleStatus?: string | null
  attributes?: Record<string, unknown> | null
}

export interface UpdateAssetRequest {
  assetName?: string
  ownerUserId?: string | null
  ownerOrgId?: string | null
  locationId?: string | null
  costCenterId?: string | null
  responsibleUserId?: string | null
  serialNo?: string | null
  brand?: string | null
  model?: string | null
  vendor?: string | null
  warrantyEndDate?: string | null
  licenseEndDate?: string | null
  lifecycleStatus?: string | null
  attributes?: Record<string, unknown> | null
}

export interface AssetRelationDto {
  id: string
  sourceAssetId: string
  targetAssetId: string
  relationType: string
  description: string | null
  createdAt: string
}

export interface CreateRelationRequest {
  targetAssetId: string
  relationType: string
  description?: string | null
}

// ============================================================================
// MVP-2 生命周期状态机（与《MVP2-接口契约.md》严格对应）
// ============================================================================

/** 生命周期当前状态 + 模板信息（E1 响应 data） */
export interface LifecycleStatus {
  assetId: string
  templateId: string
  templateName: string
  currentState: string
  currentStateName: string
  assetKind: string
}

/** 当前状态下可执行动作（E3 响应 data 数组元素） */
export interface LifecycleAction {
  actionCode: string
  actionName: string
  toState: string
  toStateName: string
  requireApproval: boolean
  requireAttachment: boolean
  guardRule: {
    requireFields?: string[]
    requireAttributeFields?: string[]
    requireAttachment?: boolean
  }
}

/** 生命周期事件（E2 响应 data 数组元素，按 createdAt 倒序） */
export interface LifecycleEvent {
  id: string
  actionCode: string
  actionName: string
  fromState: string
  toState: string
  operatorId: string
  operatorName: string | null
  reason: string | null
  formData: Record<string, unknown>
  attachmentIds: string[]
  createdAt: string
}

/** 执行动作请求体（E4 请求体） */
export interface ExecuteLifecycleActionRequest {
  reason: string
  formData?: Record<string, unknown>
  attachmentIds?: string[]
}

/** 执行动作结果（E4 响应 data）：transitioned=流转成功；approval_required=需审批（MVP-2 仅占位） */
export interface LifecycleActionResult {
  result: 'transitioned' | 'approval_required'
  fromState: string | null
  toState: string | null
  approvalInstanceId: string | null
  eventId: string | null
}
