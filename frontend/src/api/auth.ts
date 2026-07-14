import { get, post } from '../utils/request'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  MenuNode,
  RefreshRequest,
  TenantBrief,
  TokenResponse,
  UserInfoResponse
} from '../types'

// POST /api/v1/auth/login  {username,password}
export const login = (data: LoginRequest) =>
  post<LoginResponse>('/v1/auth/login', data)

// POST /api/v1/auth/refresh  {refreshToken}
export const refresh = (data: RefreshRequest) =>
  post<TokenResponse>('/v1/auth/refresh', data)

// POST /api/v1/auth/logout
export const logout = () => post<void>('/v1/auth/logout')

// POST /api/v1/auth/change-password  {oldPassword,newPassword}
export const changePassword = (data: ChangePasswordRequest) =>
  post<void>('/v1/auth/change-password', data)

// GET /api/v1/auth/me
export const getMe = () => get<UserInfoResponse>('/v1/auth/me')

// GET /api/v1/auth/menu
export const getMenu = () => get<MenuNode[]>('/v1/auth/menu')

// GET /api/v1/auth/permissions
export const getPermissions = () => get<string[]>('/v1/auth/permissions')

// GET /api/v1/auth/tenants
export const getTenants = () => get<TenantBrief[]>('/v1/auth/tenants')

// POST /api/v1/auth/tenant-switch  {tenantId}
export const switchTenant = (tenantId: string) =>
  post<TokenResponse>('/v1/auth/tenant-switch', { tenantId })
