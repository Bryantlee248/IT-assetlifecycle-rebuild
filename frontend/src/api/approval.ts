// MVP-3 审批接口封装。所有路径相对 baseURL（/api），后端统一前缀 /v1/approvals。
// 请求拦截器会自动解包 { code, message, data, traceId } 信封；非 0 code 时
// 拦截器已 ElMessage.error(message) 并 reject，业务层只需 try/catch 读取 message。
import { get, post } from '../utils/request'
import type {
  ApprovalTask,
  ApprovalInstance,
  InstanceStatus,
  TaskStatus,
  DecisionRequest
} from '../types'

/** 我的待办：当前用户为 pending 审批人的任务（可选状态过滤）。 */
export const getMyTodos = (status?: TaskStatus) =>
  get<ApprovalTask[]>('/v1/approvals/tasks/my', status ? { status } : undefined)

/** 审批实例列表（可选状态过滤）。 */
export const getApprovalInstances = (status?: InstanceStatus) =>
  get<ApprovalInstance[]>('/v1/approvals/instances', status ? { status } : undefined)

/** 审批实例详情（基础信息 + 业务上下文 + 任务历史）。 */
export const getApprovalInstance = (id: string) =>
  get<ApprovalInstance>(`/v1/approvals/instances/${id}`)

/** 通过审批（末节点回调流转 + 写事件 + 通知申请人；非末节点建下一节点）。 */
export const approveInstance = (id: string, comment?: string) =>
  post<ApprovalInstance>(`/v1/approvals/instances/${id}/approve`, { comment } as DecisionRequest)

/** 驳回审批（实例/任务=rejected，资产状态不变、不写事件，通知申请人）。 */
export const rejectInstance = (id: string, comment: string) =>
  post<ApprovalInstance>(`/v1/approvals/instances/${id}/reject`, { comment } as DecisionRequest)
