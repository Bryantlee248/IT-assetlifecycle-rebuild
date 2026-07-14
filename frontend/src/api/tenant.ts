import { del, get, post, put } from '../utils/request'
import type {
  CreateOrgRequest,
  CreateRoleRequest,
  CreateUserRequest,
  OrgNode,
  PageResult,
  RolePermissionResponse,
  RoleResponse,
  UpdateOrgRequest,
  UpdateRoleRequest,
  UpdateUserRequest,
  UserResponse
} from '../types'

// ===== 组织 =====

// GET /api/v1/tenant/organizations/tree
export const orgTree = () => get<OrgNode[]>('/v1/tenant/organizations/tree')

// POST /api/v1/tenant/organizations  {parentId,name,code,type,sort}
export const createOrg = (data: CreateOrgRequest) =>
  post<OrgNode>('/v1/tenant/organizations', data)

// PUT /api/v1/tenant/organizations/{id}  {parentId,name,code,type,sort,status}
export const updateOrg = (id: string, data: UpdateOrgRequest) =>
  put<OrgNode>(`/v1/tenant/organizations/${id}`, data)

// DELETE /api/v1/tenant/organizations/{id}
export const deleteOrg = (id: string) =>
  del<void>(`/v1/tenant/organizations/${id}`)

// ===== 用户 =====

// GET /api/v1/tenant/users?page&size&keyword
export const listUsers = (page = 1, size = 20, keyword?: string) =>
  get<PageResult<UserResponse>>('/v1/tenant/users', { page, size, keyword })

// POST /api/v1/tenant/users
export const createUser = (data: CreateUserRequest) =>
  post<UserResponse>('/v1/tenant/users', data)

// PUT /api/v1/tenant/users/{id}
export const updateUser = (id: string, data: UpdateUserRequest) =>
  put<UserResponse>(`/v1/tenant/users/${id}`, data)

// DELETE /api/v1/tenant/users/{id}
export const deleteUser = (id: string) => del<void>(`/v1/tenant/users/${id}`)

// ===== 角色 =====

// GET /api/v1/tenant/roles
export const listRoles = () => get<RoleResponse[]>('/v1/tenant/roles')

// POST /api/v1/tenant/roles
export const createRole = (data: CreateRoleRequest) =>
  post<RoleResponse>('/v1/tenant/roles', data)

// PUT /api/v1/tenant/roles/{id}
export const updateRole = (id: string, data: UpdateRoleRequest) =>
  put<RoleResponse>(`/v1/tenant/roles/${id}`, data)

// DELETE /api/v1/tenant/roles/{id}
export const deleteRole = (id: string) => del<void>(`/v1/tenant/roles/${id}`)

// GET /api/v1/tenant/roles/{id}/permissions
export const getRolePermissions = (id: string) =>
  get<RolePermissionResponse>(`/v1/tenant/roles/${id}/permissions`)

// PUT /api/v1/tenant/roles/{id}/permissions  body: List<String>
export const setRolePermissions = (id: string, permissions: string[]) =>
  put<RolePermissionResponse>(`/v1/tenant/roles/${id}/permissions`, permissions)
