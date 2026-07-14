import { get, post } from '../utils/request'
import type { CreateTenantRequest, PageResult, TenantResponse } from '../types'

// GET /api/v1/platform/tenants?page&size&keyword
export const listTenants = (page = 1, size = 20, keyword?: string) =>
  get<PageResult<TenantResponse>>('/v1/platform/tenants', { page, size, keyword })

// POST /api/v1/platform/tenants  {name,code,description}
export const createTenant = (data: CreateTenantRequest) =>
  post<TenantResponse>('/v1/platform/tenants', data)

// POST /api/v1/platform/tenants/{id}/enable
export const enableTenant = (id: string) =>
  post<void>(`/v1/platform/tenants/${id}/enable`)

// POST /api/v1/platform/tenants/{id}/disable
export const disableTenant = (id: string) =>
  post<void>(`/v1/platform/tenants/${id}/disable`)

// 别名（语义化）
export const updateTenantStatus = (id: string, enabled: boolean) =>
  enabled ? enableTenant(id) : disableTenant(id)
