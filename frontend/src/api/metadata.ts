// MVP-1 元数据接口封装：资产类型 / 字段定义 / 表单·列表·查询配置 / 运行时元数据 / 位置树。
// 所有路径相对 baseURL（/api），后端统一前缀 /v1/metadata。
import { get, patch, post, put } from '../utils/request'
import type {
  AssetTypeNode,
  AssetTypeResponse,
  CreateAssetTypeRequest,
  UpdateAssetTypeRequest,
  FieldDefinitionResponse,
  CreateFieldRequest,
  UpdateFieldRequest,
  FormSchemaResponse,
  ListViewResponse,
  SearchSchemaResponse,
  RuntimeMetadataResponse,
  LocationNode
} from '../types'

// ===== 资产类型 =====
export const getAssetTypeTree = () =>
  get<AssetTypeNode[]>('/v1/metadata/asset-types/tree')

export const createAssetType = (data: CreateAssetTypeRequest) =>
  post<AssetTypeResponse>('/v1/metadata/asset-types', data)

export const updateAssetType = (id: string, data: UpdateAssetTypeRequest) =>
  put<AssetTypeResponse>(`/v1/metadata/asset-types/${id}`, data)

export const setAssetTypeStatus = (id: string, enabled: boolean) =>
  patch<void>(`/v1/metadata/asset-types/${id}/status?enabled=${enabled}`, {})

// ===== 字段定义 =====
export const listFields = (typeId: string) =>
  get<FieldDefinitionResponse[]>(`/v1/metadata/asset-types/${typeId}/fields`)

export const createField = (typeId: string, data: CreateFieldRequest) =>
  post<FieldDefinitionResponse>(`/v1/metadata/asset-types/${typeId}/fields`, data)

export const updateField = (fieldId: string, data: UpdateFieldRequest) =>
  put<FieldDefinitionResponse>(`/v1/metadata/fields/${fieldId}`, data)

export const setFieldStatus = (fieldId: string, enabled: boolean) =>
  patch<void>(`/v1/metadata/fields/${fieldId}/status?enabled=${enabled}`, {})

// ===== 表单 / 列表 / 查询 配置 =====
export const getFormSchema = (typeId: string) =>
  get<FormSchemaResponse>(`/v1/metadata/asset-types/${typeId}/form-schema`)

export const putFormSchema = (typeId: string, schemaJson: Record<string, unknown>) =>
  put<FormSchemaResponse>(`/v1/metadata/asset-types/${typeId}/form-schema`, schemaJson)

export const getListView = (typeId: string) =>
  get<ListViewResponse>(`/v1/metadata/asset-types/${typeId}/list-view`)

export const putListView = (typeId: string, schemaJson: Record<string, unknown>) =>
  put<ListViewResponse>(`/v1/metadata/asset-types/${typeId}/list-view`, schemaJson)

export const getSearchSchema = (typeId: string) =>
  get<SearchSchemaResponse>(`/v1/metadata/asset-types/${typeId}/search-schema`)

export const putSearchSchema = (typeId: string, schemaJson: Record<string, unknown>) =>
  put<SearchSchemaResponse>(`/v1/metadata/asset-types/${typeId}/search-schema`, schemaJson)

// ===== 运行时元数据聚合（前端渲染驱动）=====
export const getRuntimeMetadata = (typeId: string) =>
  get<RuntimeMetadataResponse>(`/v1/metadata/runtime/asset-types/${typeId}`)

// ===== 位置树 =====
export const getLocationTree = () =>
  get<LocationNode[]>('/v1/metadata/locations/tree')
