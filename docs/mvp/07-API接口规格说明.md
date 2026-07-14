# 07-API接口规格说明

## 0. API 公共约定

基础路径：

```text
/api/v1
```

认证头：

```text
Authorization: Bearer <access_token>
```

API Token 调用也使用 Bearer 头，服务端根据 token 哈希识别租户和 scopes。租户业务接口不接受前端传入的 `tenant_id` 作为可信上下文。

统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "018ff5d2b6e4471c9d0f7a2d7c9d1a01"
}
```

分页响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "page": 1,
    "size": 20,
    "total": 135,
    "items": []
  },
  "traceId": "018ff5d2b6e4471c9d0f7a2d7c9d1a01"
}
```

分页参数：

```text
page: 从 1 开始
size: 默认 20，最大 200
sort: field,asc 或 field,desc
```

过滤参数：

- 简单等值：`?assetTypeId=uuid&status=running`
- 模糊搜索：`?keyword=数据库`
- 时间范围：`?createdFrom=2026-01-01T00:00:00Z&createdTo=2026-12-31T23:59:59Z`
- 多值：`?status=running,maintenance`

ID 使用 UUID 字符串，时间使用 ISO-8601 UTC 或带时区格式。

标准错误码：

| HTTP | code | 含义 |
|---|---|---|
| 400 | BAD_REQUEST | 请求格式错误 |
| 401 | UNAUTHORIZED | 未登录或 token 无效 |
| 403 | FORBIDDEN | 无权限或跨租户访问 |
| 404 | NOT_FOUND | 资源不存在或不可见 |
| 409 | CONFLICT | 并发冲突、唯一冲突、状态已变化 |
| 422 | BUSINESS_RULE_VIOLATION | 守卫规则、状态规则、字段校验失败 |
| 429 | RATE_LIMITED | 超过限流 |
| 500 | INTERNAL_ERROR | 服务端错误 |

限流：

- 登录、刷新 token、API Token 调用应启用限流。
- MVP 可先按 IP + 用户/API Token 维度实现。

接口权限标注：

- `[platform]` 仅平台用户可访问。
- `[tenant]` 仅租户上下文可访问。
- `[token]` 可由 API Token 访问，但必须满足 scopes。

## 1. API 原则

原则：

- REST 风格为主。
- 所有接口生成 OpenAPI 文档。
- 业务接口不信任前端传入的 `tenant_id`。
- 查询接口自动应用数据权限。
- 响应和导出自动应用字段权限。
- 生命周期状态只能通过动作接口改变。
- 写操作必须写审计。

## 2. 认证接口

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/change-password
GET  /api/v1/auth/me
GET  /api/v1/auth/menus
GET  /api/v1/auth/permissions
GET  /api/v1/auth/tenants
POST /api/v1/auth/switch-tenant
GET  /api/v1/health
```

登录响应：

```json
{
  "accessToken": "jwt",
  "refreshToken": "jwt",
  "mustChangePassword": false,
  "tenant": { "id": "uuid", "name": "默认租户" },
  "user": { "id": "uuid", "name": "张三", "roles": ["asset_admin"] }
}
```

刷新 token 必须轮换 refresh token，旧 refresh token 立即失效。

改密请求：

```json
{
  "oldPassword": "OldPassword123!",
  "newPassword": "NewPassword123!"
}
```

## 3. 租户和组织接口

```text
POST /api/v1/platform/tenants              [platform: platform_admin]
GET  /api/v1/platform/tenants              [platform: platform_admin/platform_auditor]
GET  /api/v1/platform/tenants/{tenantId}   [platform: platform_admin/platform_auditor]
PATCH /api/v1/platform/tenants/{tenantId}/status [platform: platform_admin]

GET  /api/v1/orgs/tree                     [tenant]
POST /api/v1/orgs                          [tenant: tenant_admin]
PUT  /api/v1/orgs/{orgId}                  [tenant: tenant_admin]
DELETE /api/v1/orgs/{orgId}                [tenant: tenant_admin]
```

平台用户无租户上下文，不能调用 `/api/v1/assets` 等租户业务接口。

## 4. 用户和权限接口

```text
GET  /api/v1/users
POST /api/v1/users
GET  /api/v1/users/{userId}
PUT  /api/v1/users/{userId}
PATCH /api/v1/users/{userId}/status
POST /api/v1/users/{userId}/roles

GET  /api/v1/roles
POST /api/v1/roles
PUT  /api/v1/roles/{roleId}
DELETE /api/v1/roles/{roleId}

GET  /api/v1/permissions
PUT  /api/v1/roles/{roleId}/permissions
GET  /api/v1/roles/{roleId}/data-scope-rules
PUT  /api/v1/roles/{roleId}/data-scope-rules
GET  /api/v1/roles/{roleId}/field-rules
PUT  /api/v1/roles/{roleId}/field-rules
GET  /api/v1/roles/{roleId}/state-rules
PUT  /api/v1/roles/{roleId}/state-rules
```

权限规则写入请求必须完整覆盖角色规则，不允许前端只传展示字段后导致权限丢失。

## 5. 元数据接口

```text
GET  /api/v1/metadata/asset-types/tree
POST /api/v1/metadata/asset-types
PUT  /api/v1/metadata/asset-types/{typeId}
PATCH /api/v1/metadata/asset-types/{typeId}/status

GET  /api/v1/metadata/asset-types/{typeId}/fields
POST /api/v1/metadata/asset-types/{typeId}/fields
PUT  /api/v1/metadata/fields/{fieldId}
PATCH /api/v1/metadata/fields/{fieldId}/status

GET /api/v1/metadata/asset-types/{typeId}/form-schema
PUT /api/v1/metadata/asset-types/{typeId}/form-schema
GET /api/v1/metadata/asset-types/{typeId}/list-view
PUT /api/v1/metadata/asset-types/{typeId}/list-view
GET /api/v1/metadata/asset-types/{typeId}/search-schema
PUT /api/v1/metadata/asset-types/{typeId}/search-schema
GET /api/v1/metadata/runtime/asset-types/{typeId}
```

字段定义若设置 `unique_scope != none` 且字段不是物理列，后端必须拒绝保存或生成可落地索引方案。

## 6. 资产接口

```text
GET  /api/v1/assets
POST /api/v1/assets
GET  /api/v1/assets/{assetId}
PUT  /api/v1/assets/{assetId}
DELETE /api/v1/assets/{assetId}
POST /api/v1/assets/batch-update
GET  /api/v1/assets/{assetId}/relations
POST /api/v1/assets/{assetId}/relations
DELETE /api/v1/assets/{assetId}/relations/{relationId}
```

资产列表支持分页、筛选、排序、字段权限过滤。

资产创建请求：

```json
{
  "assetTypeId": "uuid",
  "assetName": "核心数据库服务器01",
  "assetNo": "SRV-2026-0001",
  "ownerOrgId": "uuid",
  "responsibleUserId": "uuid",
  "locationId": "uuid",
  "serialNo": "SN123456",
  "brand": "Dell",
  "model": "R750",
  "attributes": {
    "memory_gb": 512
  }
}
```

状态字段 `lifecycleStatus` 不允许通过资产创建/编辑接口直接传入覆盖。

## 7. 采购接口

```text
GET  /api/v1/procurement/requests
POST /api/v1/procurement/requests
GET  /api/v1/procurement/requests/{id}
PUT  /api/v1/procurement/requests/{id}
POST /api/v1/procurement/requests/{id}/submit
POST /api/v1/procurement/requests/{id}/cancel
```

采购申请可关联资产，但不是资产创建的强制前置。

## 8. 生命周期接口

```text
GET  /api/v1/lifecycle/templates
POST /api/v1/lifecycle/templates
GET  /api/v1/lifecycle/templates/{templateId}
PUT  /api/v1/lifecycle/templates/{templateId}
DELETE /api/v1/lifecycle/templates/{templateId}
GET  /api/v1/lifecycle/templates/{templateId}/transitions
PUT  /api/v1/lifecycle/templates/{templateId}/transitions

GET  /api/v1/assets/{assetId}/lifecycle
GET  /api/v1/assets/{assetId}/lifecycle/events
GET  /api/v1/assets/{assetId}/lifecycle/actions
POST /api/v1/assets/{assetId}/lifecycle/actions/{actionCode}
```

动作请求：

```json
{
  "reason": "设备到货确认入库",
  "formData": {},
  "attachmentIds": ["uuid"]
}
```

不需审批或 MVP-2 免审批执行响应：

```json
{
  "result": "transitioned",
  "fromState": "purchasing",
  "toState": "inbound",
  "eventId": "uuid"
}
```

需要审批响应：

```json
{
  "result": "approval_required",
  "approvalInstanceId": "uuid",
  "currentState": "running"
}
```

非法流转返回 `422 BUSINESS_RULE_VIOLATION`；并发导致当前状态已变化返回 `409 CONFLICT`。

## 9. 审批接口

```text
GET  /api/v1/approvals/tasks/my?page=1&size=20&status=pending
GET  /api/v1/approvals/instances?page=1&size=20&status=pending&refType=asset
GET  /api/v1/approvals/instances/{id}
POST /api/v1/approvals/instances/{id}/approve
POST /api/v1/approvals/instances/{id}/reject
POST /api/v1/approvals/instances/{id}/cancel
POST /api/v1/approvals/instances/{id}/resubmit
GET  /api/v1/approvals/templates
POST /api/v1/approvals/templates
PUT  /api/v1/approvals/templates/{templateId}
DELETE /api/v1/approvals/templates/{templateId}
```

审批处理请求：

```json
{
  "comment": "同意",
  "formData": {}
}
```

驳回、撤销、重新提交规则：

- `reject`：审批实例变为 `rejected`，资产状态不变，写审批事件和通知。
- `cancel`：仅申请人或租户管理员可撤销 pending 实例，资产状态不变。
- `resubmit`：基于原动作上下文创建新的审批实例，原实例保持 `rejected` 或 `cancelled`。
- 审批通过后由服务端执行生命周期动作，前端不得自行改状态。

## 10. 软件许可证接口

```text
GET  /api/v1/licenses
POST /api/v1/licenses
GET  /api/v1/licenses/{licenseId}
PUT  /api/v1/licenses/{licenseId}
GET  /api/v1/licenses/{licenseId}/assignments
POST /api/v1/licenses/{licenseId}/assignments
POST /api/v1/licenses/{licenseId}/assignments/{assignmentId}/reclaim
POST /api/v1/licenses/{licenseId}/renew
GET  /api/v1/licenses/compliance-summary
```

许可证分配超出可用数量返回 `409 CONFLICT`。

## 11. 运营接口

```text
POST /api/v1/assets/import
GET  /api/v1/assets/import-template?assetTypeId=uuid&format=xlsx
GET  /api/v1/assets/import-jobs?page=1&size=20
GET  /api/v1/assets/import-jobs/{id}
GET  /api/v1/assets/import-jobs/{id}/errors?page=1&size=100
GET  /api/v1/assets/export?assetTypeId=uuid&format=xlsx

GET /api/v1/reports/asset-summary
GET /api/v1/reports/lifecycle-summary
GET /api/v1/reports/license-usage
GET /api/v1/reports/expiration-alerts?page=1&size=20
GET /api/v1/reports/pending-tasks?page=1&size=20

GET /api/v1/audit-logs?page=1&size=20&operatorId=uuid&action=asset.create
GET /api/v1/notifications?page=1&size=20&read=false
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all

POST /api/v1/attachments
GET  /api/v1/attachments/{id}
DELETE /api/v1/attachments/{id}
```

导入：

- 支持 `xlsx` 和 `csv`。
- 使用异步任务，创建后返回 `jobId`。
- 同一文件可使用 `Idempotency-Key` 防止重复提交。
- 行级错误通过 `/errors` 分页查询。

附件上传：

```text
Content-Type: multipart/form-data
fields:
  file: binary
  refType: asset/approval/purchase/license
  refId: uuid
```

附件限制：

- 单文件默认不超过 50MB。
- 后端校验 MIME、扩展名、租户上下文和对象权限。

## 12. 集成接口

```text
GET  /api/v1/integrations/api-tokens
POST /api/v1/integrations/api-tokens
PATCH /api/v1/integrations/api-tokens/{id}/revoke

GET  /api/v1/integrations/webhooks
POST /api/v1/integrations/webhooks
PUT  /api/v1/integrations/webhooks/{id}
PATCH /api/v1/integrations/webhooks/{id}/status
GET  /api/v1/integrations/webhooks/{id}/deliveries?page=1&size=20
```

创建 API Token 请求：

```json
{
  "tokenName": "CMDB 集成",
  "scopes": ["asset:read", "report:read"],
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

创建响应只返回一次明文：

```json
{
  "token": "itam_xxx",
  "tokenId": "uuid"
}
```

Webhook 投递必须带签名：

```text
X-ITAM-Event: asset.updated
X-ITAM-Timestamp: 1783526400
X-ITAM-Signature: sha256=<hmac>
```

签名内容为 `timestamp + "." + rawBody`，算法为 HMAC-SHA256。
