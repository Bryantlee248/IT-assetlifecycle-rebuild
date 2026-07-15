// MVP-2 生命周期接口封装：状态 / 事件 / 动作 / 执行动作。
// 所有路径相对 baseURL（/api），后端统一前缀 /v1/assets/{id}/lifecycle。
// 请求拦截器会自动解包 { code, message, data, traceId } 信封；非 0 code 时
// 拦截器已 ElMessage.error(message) 并 reject，业务层只需 try/catch 读取 message。
import { get, post } from '../utils/request'
import type {
  LifecycleStatus,
  LifecycleEvent,
  LifecycleAction,
  ExecuteLifecycleActionRequest,
  LifecycleActionResult
} from '../types'

/** E1 当前生命周期状态 + 模板信息 */
export const getLifecycle = (id: string) =>
  get<LifecycleStatus>(`/v1/assets/${id}/lifecycle`)

/** E2 生命周期事件时间线（倒序） */
export const getLifecycleEvents = (id: string) =>
  get<LifecycleEvent[]>(`/v1/assets/${id}/lifecycle/events`)

/** E3 当前状态下可执行动作列表 */
export const getLifecycleActions = (id: string) =>
  get<LifecycleAction[]>(`/v1/assets/${id}/lifecycle/actions`)

/** E4 执行生命周期动作（闭环核心） */
export const executeLifecycleAction = (
  id: string,
  actionCode: string,
  data: ExecuteLifecycleActionRequest
) =>
  post<LifecycleActionResult>(
    `/v1/assets/${id}/lifecycle/actions/${actionCode}`,
    data
  )
