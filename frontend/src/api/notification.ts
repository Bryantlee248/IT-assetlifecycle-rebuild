// MVP-3 通知接口封装。所有路径相对 baseURL（/api），后端统一前缀 /v1/notifications。
// 双隔离（tenant_id + receiver_id）由服务端强校验，前端仅正常展示。
import { get, post } from '../utils/request'
import type { Notification, NotificationType } from '../types'

/** 我的通知列表（可选类型过滤）。 */
export const getNotifications = (type?: NotificationType) =>
  get<Notification[]>('/v1/notifications', type ? { type } : undefined)

/** 未读数（receiver 下 read_at 为空的数量）。 */
export const getUnreadCount = () =>
  get<number>('/v1/notifications/unread-count')

/** 标记单条已读（服务端强校验 receiver_id，越权按 40401）。 */
export const markNotificationRead = (id: string) =>
  post<void>(`/v1/notifications/${id}/read`)

/** 全部已读。 */
export const markAllNotificationsRead = () =>
  post<void>('/v1/notifications/read-all')
