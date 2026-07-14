// MVP-1 资产接口封装：列表/创建/详情/编辑/删除 + 资产关系。
// 所有路径相对 baseURL（/api），后端统一前缀 /v1/assets。
import { del, get, post, put } from '../utils/request'
import type {
  AssetResponse,
  AssetListItem,
  PageResult,
  AssetQuery,
  CreateAssetRequest,
  UpdateAssetRequest,
  AssetRelationDto,
  CreateRelationRequest
} from '../types'

export const listAssets = (q: AssetQuery) =>
  get<PageResult<AssetListItem>>('/v1/assets', q as Record<string, unknown>)

export const createAsset = (data: CreateAssetRequest) =>
  post<AssetResponse>('/v1/assets', data)

export const getAsset = (id: string) =>
  get<AssetResponse>(`/v1/assets/${id}`)

export const updateAsset = (id: string, data: UpdateAssetRequest) =>
  put<AssetResponse>(`/v1/assets/${id}`, data)

export const deleteAsset = (id: string) =>
  del<void>(`/v1/assets/${id}`)

// ===== 资产关系 =====
export const listRelations = (id: string) =>
  get<AssetRelationDto[]>(`/v1/assets/${id}/relations`)

export const createRelation = (id: string, data: CreateRelationRequest) =>
  post<AssetRelationDto>(`/v1/assets/${id}/relations`, data)

export const deleteRelation = (assetId: string, relationId: string) =>
  del<void>(`/v1/assets/${assetId}/relations/${relationId}`)
