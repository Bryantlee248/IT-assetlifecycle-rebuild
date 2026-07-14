# MVP-1 接口契约补充（整改）

> 配套文档：`07-API接口规格说明.md`（基础契约）、`MVP1-设计-高见远.md`（架构）、`MVP1-整改报告.md`（交付）。
> 本文档仅记录 **MVP-1 整改引入的契约级变更**（P0-1~P0-5），不涉及 MVP-2。

技术栈：Spring Boot 3 + Java 21，统一前缀 `/api/v1`（网关/代理已配置），响应统一信封 `ApiResponse<T>`。

---

## 1. 统一响应信封

```json
{ "code": 0, "message": "success", "data": <T>, "traceId": "..." }
```

分页列表（`/v1/assets` GET）的 `data` 为：

```json
{ "page": 1, "size": 20, "total": 137, "list": [ <AssetListItem>, ... ] }
```

- `code = 0` 表示成功；非 0 见第 2 节错误码表。
- 失败时 `data` 为 `null`，`message` 为可读错误描述。

---

## 2. 错误码与 HTTP 状态码映射（新增）

| ResultCode | code | HTTP | 触发场景 |
|---|---|---|---|
| `NO_PERMISSION` | 40300 | 403 | 字段无写入权限（P0-1）、越权写 |
| `ASSET_NOT_FOUND` | 40401 | 404 | 资产不存在 / 跨租户 / `asset_user` 访问非本人非责任人资产（P0-2） |
| `BUSINESS_RULE_VIOLATION` | 42200 | 422 | 非法关系类型（P0-4）、自环、重复关系 |
| `CONFLICT` / `ASSET_NO_CONFLICT` / `SERIAL_NO_CONFLICT` | 40900/40901/40902 | 409 | 资产编号/序列号重复 |
| `PARAM_ERROR` | 40000 | 400 | 参数校验失败（含 `@Valid` 失败） |
| `UNAUTHENTICATED` | 40100 | 401 | 未认证或登录失效 |

> 映射实现见 `GlobalExceptionHandler.toHttpStatus`：
> `NO_PERMISSION/MUST_CHANGE_PASSWORD → 403`，`NOT_FOUND/ASSET_NOT_FOUND → 404`，
> `BUSINESS_RULE_VIOLATION/FIELD_UNIQUE_REJECTED → 422`，`RATE_LIMITED → 429`。

---

## 3. 资产接口（字段权限生效）

基路径：`/api/v1/assets`。`tenant_id` 与角色码（`roleCode`）取自 JWT principal，**前端不可伪造**。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/v1/assets` | `asset:view` | 列表（注入数据范围，P0-2） |
| POST | `/v1/assets` | `asset:create` | 创建（lifecycleStatus 固定 `planned`，P0-1） |
| GET | `/v1/assets/{assetId}` | `asset:view` | 详情（数据范围校验，P0-2） |
| PUT | `/v1/assets/{assetId}` | `asset:update` | 编辑（忽略请求中的 lifecycleStatus，P0-1） |
| DELETE | `/v1/assets/{assetId}` | `asset:delete` | 软删 |

### 3.1 写路径字段权限（P0-1，后端权威）

- 请求体中 **`visible=false` 或 `editable=false` 的字段一律拒绝写入**，返回 `403 NO_PERMISSION`，**不静默覆盖**。
- 校验覆盖：固定物理字段（如 `asset_name`、`owner_user_id` 等热点列）**与**动态属性（`attributes` 内字段）均受 `FieldPermissionService.resolve()` 控制。
- `lifecycleStatus`：创建时强制为 `planned`（忽略请求值）；编辑时忽略请求值，保持原值（MVP-1 由系统控制）。

### 3.2 响应字段过滤 / 脱敏 / 加密（P0-2 / P0-5）

响应（详情 `AssetResponse`、列表 `AssetListItem`）按角色套用字段权限：

- `visible=false` → 字段**不返回**（`fields` Map 剔除；顶层热点固定字段返回 `null`，不泄露）。
- `masked=true` → 敏感值按 `maskRule` 脱敏（如 `license_key` → `***1234`）。
- `storageType=encrypted` 的字段 → 落库前 AES-256-GCM 加密（`enc:` 前缀），响应时解密后再按权限脱敏；**明文不在库中留存**。
- 审计日志详情中**不含敏感明文**（如 `license_key` 的明文值）。

### 3.3 数据范围（P0-2）

- `asset_user`：列表/详情仅返回 `owner_user_id` 或 `responsible_user_id` 为当前用户的资产；访问他人资产按 `404 ASSET_NOT_FOUND` 处理（与跨租户一致）。
- `auditor` / `asset_operator`：按各自权限矩阵可见/只读/脱敏（见权限矩阵）。

---

## 4. 资产关系接口（枚举约束，P0-4）

基路径：`/api/v1/assets/{assetId}/relations`。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/v1/assets/{assetId}/relations` | `asset:view` | 列出源资产的关系 |
| POST | `/v1/assets/{assetId}/relations` | `asset:update` | 创建关系 |
| DELETE | `/v1/assets/{assetId}/relations/{relationId}` | `asset:delete` | 删除关系（软删） |

请求体 `CreateRelationRequest`：

```json
{ "targetAssetId": "<uuid>", "relationType": "installed_on", "description": "可选" }
```

校验规则（应用层 + DB 双层，见 V5 迁移）：

1. **关系类型白名单**：`installed_on` / `binds_to` / `depends_on` / `located_in` / `uses`。非法值 → `422 BUSINESS_RULE_VIOLATION`。
2. **禁止自环**：`sourceAssetId == targetAssetId` → `422 BUSINESS_RULE_VIOLATION`。
3. **源/目标必须同租户**：目标不存在或跨租户 → `404 ASSET_NOT_FOUND`。
4. **不可重复**：同一 `(source, target, type)` 已存在 → `422 BUSINESS_RULE_VIOLATION`。

DB 兜底：迁移 `V5__asset_relation_constraint.sql` 增加 `chk_asset_relation_type` CHECK 约束（不修改 V1~V4）。

---

## 5. 权限矩阵（P0-2，5 角色）

| 角色 | 可见 | 可编辑非系统字段 | 敏感可编辑 | 敏感脱敏 | 可导出 | 敏感可导出 |
|---|---|---|---|---|---|---|
| `tenant_admin` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| `asset_admin` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| `asset_operator` | ✅ | ❌（仅运维字段） | ❌ | ✅ | ❌ | ❌ |
| `auditor` | ✅（只读） | ❌ | ❌ | ✅ | ❌ | ❌ |
| `asset_user` | 仅本人/责任人 | ❌（只读） | ❌ | 敏感**隐藏** | ❌ | ❌ |

> 系统字段（如 `id`、`tenant_id`、`created_at`、审计字段）恒不可编辑，对所有角色 `editable=false`。

---

## 6. 迁移与向后兼容

- 新增 `V5__asset_relation_constraint.sql`（仅增量约束，不动 V1~V4）。
- 前端运行时元数据兼容 V4 种子字段 `field` / `title` + `filters`，**不改动种子数据**，由前端适配层兼容。
- 统一响应信封、权限模型、审计模型、Flyway 体系均保持不变。
