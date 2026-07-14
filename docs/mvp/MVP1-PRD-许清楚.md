# MVP-1 元数据与资产核心 — 产品需求文档（PRD）

> 作者：产品经理 许清楚（Xu）
> 阶段：MVP-1（基于 MVP-0 平台底座）
> 项目代号：`mvp1_metadata_asset_core`
> 技术栈（固定，不得自行切换）：Spring Boot 3 + Java 21 / PostgreSQL / Flyway / Vue 3 + TypeScript + Vite + Element Plus
> 配套依据文档：`01~03, 05~08, 10, 12, 13, 16`（见 `docs/mvp/`）

---

## 1. 产品目标

**一句话目标**
在 MVP-0 已验证的平台底座（认证/租户/角色/权限/强制改密/审计/OpenAPI）之上，交付「元数据驱动 + 资产核心」，使租户管理员与资产管理员**无需修改代码**即可配置资产类型、字段、表单、列表、查询，并完成资产台账的增删改查、动态字段存储、字段权限管控与资产关系建模。

**MVP-1 北极星指标**
| 指标 | 目标 | 度量方式 |
|---|---|---|
| 元数据驱动覆盖率 | 4 类预置资产类型 100% 由元数据配置驱动，无硬编码字段页 | 自动化检查运行时元数据接口返回与渲染 |
| 字段权限命中率 | 无权限字段在 列表/详情/表单/API 响应 100% 被后端过滤 | 权限测试 M1-06、S-02 全绿 |
| 租户隔离命中率 | 跨租户访问 100% 被拦截（403/404） | 隔离测试 M1-07、S-01 全绿 |
| 唯一约束兜底率 | 重复 `asset_no`/`serial_no` 并发写入 100% 由数据库部分唯一索引兜底（409） | 并发测试 C-02、M1-05 全绿 |
| 操作可审计率 | 元数据变更 + 资产增/改/删 100% 写审计日志 | 审计核对（M0/M1 验收） |

---

## 2. 用户故事（MVP-1 相关，引用 `02-MVP功能清单与用户故事.md`）

- **US-1**（作为租户管理员，我可以创建资产类型，以便管理不同资产。）→ 对应资产类型树与 CRUD。
- **US-2**（作为租户管理员，我可以为资产类型配置字段，以便不用修改代码就扩展字段。）→ 对应字段定义 + 热点/唯一/敏感规则。
- **US-3**（作为资产管理员，我可以创建服务器资产，以便记录机房设备。）→ 对应资产新增 + 动态字段落库。
- **US-4**（作为软件管理员，我可以创建软件许可证资产，以便记录授权信息。）→ 对应无形资产（intangible）建模 + `license_key` 敏感脱敏。
- **US-5**（作为审计员，我访问资产详情时看不到无权限字段，以便满足最小可见原则。）→ 对应字段权限后端响应过滤。

---

## 3. 需求池

> 优先级：P0 = 必须（MVP-1 验收门槛）；P1 = 重要（建议本阶段完成，缺项需主理人背书）；P2 = 可选（后续阶段或增强）。
> 每条含：编号 / 描述 / 验收要点。验收要点需可被 `12-测试用例与验收标准.md` 的 M1/S/C 用例直接引用。

### 3.1 P0（必须）

| 编号 | 描述 | 验收要点 |
|---|---|---|
| **M1-P0-01 资产类型树** | 支持资产类型树形结构（父子 `parent_id`），提供树读取与单类型增改、启停。 | `GET /api/v1/metadata/asset-types/tree` 返回层级树；`type_code` 在 `(tenant_id, deleted=false)` 下唯一（部分唯一索引）；启用/停用经 `PATCH .../status`。 |
| **M1-P0-02 字段定义** | 按资产类型定义字段（code/name/type/必填/唯一范围/默认值/校验/可搜索/可排序/可见/可编辑/敏感/加密/脱敏规则等）。 | `POST/GET/PUT/PATCH` 字段接口可用；`field_code` 在 `(tenant_id, asset_type_id, deleted=false)` 下唯一；字段编码创建后不可改（前端 + 后端双校验）。 |
| **M1-P0-03 表单配置** | 每种类型一套 `form_schema`（JSONB：分组/列数/字段顺序）。 | `GET/PUT .../form-schema` 读写；发布前前端展示影响提示；schema 含格式校验。 |
| **M1-P0-04 列表配置** | 每种类型一套 `list_view`（列字段/宽度/固定列/默认排序）。 | `GET/PUT .../list-view` 读写；无权限字段不出现于列表。 |
| **M1-P0-05 查询配置** | 每种类型一套 `search_schema`（关键字/枚举/日期范围/组织树/位置树/类型树/状态/数值范围）。 | `GET/PUT .../search-schema` 读写；查询参数与 `07` 过滤约定一致。 |
| **M1-P0-06 运行时元数据接口** | 提供运行时聚合接口，供前端渲染动态表单/列表/查询。 | `GET /api/v1/metadata/runtime/asset-types/{typeId}` 返回 字段定义 + 表单 + 列表 + 查询 + 当前用户字段权限；前端据此渲染，不为每类型写死页面。 |
| **M1-P0-07 资产新增** | 创建资产，落物理列 + `attributes` JSONB；初始状态受控。 | `POST /api/v1/assets` 成功；热点字段入物理列、扩展字段入 `attributes`；**不得接受 `lifecycleStatus`**；初始状态 = 类型初始状态或 `planned`；写审计。 |
| **M1-P0-08 资产编辑** | 编辑资产，热点字段与 `attributes` 均可更新。 | `PUT /api/v1/assets/{id}` 成功；**忽略/拒绝 `lifecycleStatus`**；只读字段（字段权限）不可被覆盖提交；写审计。 |
| **M1-P0-09 资产详情** | 单资产详情，含动态字段、关系、基础信息。 | `GET /api/v1/assets/{id}` 返回经字段权限过滤后的数据；敏感字段脱敏/隐藏；字段权限在响应体已生效。 |
| **M1-P0-10 资产列表** | 分页/筛选/排序/字段权限过滤的资产列表。 | `GET /api/v1/assets` 支持 `page/size/sort`、等值/模糊/时间范围/多值过滤；自动注入租户与数据范围；无权限列不返回。 |
| **M1-P0-11 JSONB 动态字段** | 非热点字段存 `assets.attributes`（JSONB）。 | 创建/编辑请求 `attributes` 中的扩展字段正确落库；`attributes` 默认 `'{}'::jsonb`；GIN 索引可用。 |
| **M1-P0-12 热点字段物理列** | `assets` 必须包含文档 06 指定的全部热点物理列。 | 16 个热点列全部建表：`asset_no, asset_name, asset_kind, asset_type_id, lifecycle_status, owner_user_id, owner_org_id, responsible_user_id, location_id, cost_center_id, serial_no, brand, model, vendor, warranty_end_date, license_end_date`。 |
| **M1-P0-13 索引与唯一索引** | 建立部分唯一索引与常用查询索引 + GIN。 | `ux_assets_tenant_asset_no_active`（`tenant_id, asset_no` WHERE deleted=false）；`ux_assets_tenant_serial_no_active`（`tenant_id, serial_no` WHERE deleted=false AND serial_no IS NOT NULL）；`idx_assets_tenant_type/status/location`；`idx_assets_warranty_end/license_end`；`idx_assets_attributes_gin`（GIN on `attributes`）。Flyway 可重复执行。 |
| **M1-P0-14 唯一字段规则** | `unique_scope != none` 时：热点字段用物理列+部分唯一索引；**非热点字段后端必须拒绝发布该唯一配置**（提示升格为热点字段或生成索引方案），不能只靠应用层判断。 | 字段定义保存时校验；非热点唯一配置返回 `422 BUSINESS_RULE_VIOLATION`；热点唯一配置落地为表达式/部分唯一索引；写入事务内仍做重复检查以返回友好错误。 |
| **M1-P0-15 资产关系** | 支持资产间关系（installed_on/binds_to/depends_on/located_in/uses 等）。 | `GET/POST/DELETE .../relations` 可用；关系存 `asset_relations`，不写死在某类型；`relation_type` 受枚举约束；写审计。 |
| **M1-P0-16 字段权限基础能力** | 后端响应过滤 `visible/editable/masked/exportable`，作用于 列表/详情/表单/API 响应；对 5 个租户默认角色（`tenant_admin/asset_admin/asset_operator/asset_user/auditor`）生效，由**默认规则服务**实现；预留 `field_permission_rules` 表（配置页面留到 MVP-3）。 | 敏感字段（如 `license_key`）对无权限角色返回脱敏/隐藏；`editable=false` 字段前端只读且后端拒绝覆盖；`exportable=false` 敏感字段在导出（MVP-4）不输出；`field_permission_rules` 表结构本阶段建立但无配置 UI。 |
| **M1-P0-17 预置元数据** | 预置 4 类资产类型的字段定义与种子数据：服务器、网络设备、安全设备、软件许可证。 | 4 类类型 + 字段定义经 Flyway/种子脚本初始化；运行时接口可返回；演示资产样例可创建（见 `13`）。 |
| **M1-P0-18 租户隔离** | 所有租户业务表带 `tenant_id`；前端传入 `tenant_id` 不可信，后端从 token 解析。 | A 租户 token 访问 B 租户资产/类型返回 403 或 404（M1-07、S-01）；查询自动注入租户过滤；写操作自动填充租户。 |
| **M1-P0-19 生命周期状态受控** | 资产新增/编辑接口**不得允许直接修改 `lifecycle_status`**；初始状态默认 `planned` 或资产类型配置初始状态。 | 请求体含 `lifecycleStatus` 时被忽略或返回 `422`；创建后 `lifecycle_status` 为类型初始状态（默认 `planned`）。 |
| **M1-P0-20 关键写操作审计** | 元数据变更（类型/字段/表单/列表/查询）、资产增/改/删、关系增删必须写审计日志。 | `audit_logs` 记录 `tenant_id/operator_id/action/resource_type/resource_id/before_data/after_data/ip`；关键写操作 100% 有记录（M0/M1 验收）。 |
| **M1-P0-21 MVP-0 兼容约束** | 不修改 MVP-0 已验证的认证/租户/角色/权限/强制改密逻辑；为兼容 MVP-1 的改动必须有回归测试。 | 现有 MVP-0 用例（M0-01~M0-05）全绿；新增字段权限/租户注入不影响既有登录、改密、健康检查。 |

### 3.2 P1（重要）

| 编号 | 描述 | 验收要点 |
|---|---|---|
| **M1-P1-01 敏感字段加密存储** | `field_definitions.encrypted=true` 字段（如 `license_key`）加密落库，不存明文；API 输出脱敏。 | 库内为密文；响应仅返回脱敏值（后 4 位 + `***`）；日志/审计不输出明文。 |
| **M1-P1-02 关系类型与闭环校验** | `relation_type` 受枚举约束；防止无意义自环（可选轻度校验）。 | 非法 `relation_type` 返回 `422`；关系两端同租户。 |
| **M1-P1-03 软删与部分唯一索引协同** | 资产软删除后，`asset_no`/`serial_no` 可被同租户重新使用。 | 软删后重建同编号资产成功（C-03）；部分唯一索引 `WHERE deleted=false` 生效。 |
| **M1-P1-04 唯一范围区分** | `unique_scope` 支持 `tenant`（租户内唯一）与 `asset_type`（类型内唯一）语义。 | 索引/校验按 scope 生成；跨类型允许重复、跨租户允许重复。 |
| **M1-P1-05 runtime 元数据缓存** | 运行时元数据按 `(tenant_id, asset_type_id)` 缓存，元数据/字段权限变更触发失效。 | 缓存 key 含 `tenant_id`；改字段/类型后详情即时反映新权限（参考 M5-07 思路）。 |
| **M1-P1-06 元数据审计 before/after** | 元数据写操作记录变更前后快照。 | `before_data/after_data` 含 schema/字段 JSON 差异。 |

### 3.3 P2（可选）

| 编号 | 描述 | 验收要点 |
|---|---|---|
| **M1-P2-01 类型树拖拽排序** | 支持 `sort_order` 调整与拖拽。 | 树顺序持久化；不影响唯一性。 |
| **M1-P2-02 字段定义克隆/导入** | 跨类型克隆字段定义。 | 克隆后 `field_code` 在本类型内唯一校验通过。 |
| **M1-P2-03 列表高级配置** | 列宽/固定列/默认排序持久化增强。 | 用户列设置生效（基础列已在 P0）。 |
| **M1-P2-04 OpenAPI 覆盖** | MVP-1 全部接口生成 OpenAPI 文档并可访问。 | Swagger/OpenAPI 含 23 个 MVP-1 接口。 |

---

## 4. API 接口清单（MVP-1，context-path 前缀 `/api`）

> 共 **23** 个接口（元数据 15 + 资产 8）。以下接口必须全部实现并生成 OpenAPI 文档。`POST /api/v1/assets/batch-update` 属 MVP-1 之外的批量能力，**不纳入本阶段**；导出/导入相关接口（MVP-4）不纳入。

### 4.1 元数据（15）

| 方法 & 路径 | 关联需求 | 说明 |
|---|---|---|
| `GET /api/v1/metadata/asset-types/tree` | M1-P0-01 | 资产类型树 |
| `POST /api/v1/metadata/asset-types` | M1-P0-01 | 新建类型 |
| `PUT /api/v1/metadata/asset-types/{typeId}` | M1-P0-01 | 修改类型 |
| `PATCH /api/v1/metadata/asset-types/{typeId}/status` | M1-P0-01 | 启用/停用 |
| `GET /api/v1/metadata/asset-types/{typeId}/fields` | M1-P0-02 | 字段列表 |
| `POST /api/v1/metadata/asset-types/{typeId}/fields` | M1-P0-02, M1-P0-14 | 新建字段（含唯一规则校验） |
| `PUT /api/v1/metadata/fields/{fieldId}` | M1-P0-02 | 修改字段 |
| `PATCH /api/v1/metadata/fields/{fieldId}/status` | M1-P0-02 | 启用/停用字段 |
| `GET /api/v1/metadata/asset-types/{typeId}/form-schema` | M1-P0-03 | 读表单配置 |
| `PUT /api/v1/metadata/asset-types/{typeId}/form-schema` | M1-P0-03 | 写表单配置 |
| `GET /api/v1/metadata/asset-types/{typeId}/list-view` | M1-P0-04 | 读列表配置 |
| `PUT /api/v1/metadata/asset-types/{typeId}/list-view` | M1-P0-04 | 写列表配置 |
| `GET /api/v1/metadata/asset-types/{typeId}/search-schema` | M1-P0-05 | 读查询配置 |
| `PUT /api/v1/metadata/asset-types/{typeId}/search-schema` | M1-P0-05 | 写查询配置 |
| `GET /api/v1/metadata/runtime/asset-types/{typeId}` | M1-P0-06, M1-P0-16 | 运行时聚合元数据（含字段权限） |

### 4.2 资产（8）

| 方法 & 路径 | 关联需求 | 说明 |
|---|---|---|
| `GET /api/v1/assets` | M1-P0-10, M1-P0-16, M1-P0-18 | 资产列表（分页/筛选/排序/租户/字段权限） |
| `POST /api/v1/assets` | M1-P0-07, M1-P0-11, M1-P0-12, M1-P0-19 | 资产新增（物理列+JSONB，禁改状态） |
| `GET /api/v1/assets/{assetId}` | M1-P0-09, M1-P0-16 | 资产详情（字段权限过滤） |
| `PUT /api/v1/assets/{assetId}` | M1-P0-08, M1-P0-19 | 资产编辑（禁改状态，只读字段不可覆盖） |
| `DELETE /api/v1/assets/{assetId}` | M1-P0-20 | 资产删除（软删 + 审计） |
| `GET /api/v1/assets/{assetId}/relations` | M1-P0-15 | 关系列表 |
| `POST /api/v1/assets/{assetId}/relations` | M1-P0-15, M1-P0-20 | 新建关系 |
| `DELETE /api/v1/assets/{assetId}/relations/{relationId}` | M1-P0-15, M1-P0-20 | 删除关系 |

**公共契约（沿用 `07`）**：统一响应信封 `{code,message,data,traceId}`；分页 `{page,size,total,items}`；错误码 400/401/403/404/409/422/429/500；`tenant_id` 不可由前端信任；所有写操作写审计。

---

## 5. 字段权限默认规则（MVP-1，5 角色默认规则服务）

> 本阶段**不提供字段权限配置页面**（留 MVP-3），但 `field_permission_rules` 表必须建立；下列规则由**默认规则服务**按 `(角色, 字段属性)` 解析，作用于 列表/详情/表单/API 响应。`exportable` 标志在 MVP-1 定义并参与后端解析，实际导出执行在 MVP-4。

| 角色 | visible | editable | masked（敏感字段） | exportable | 备注 |
|---|---|---|---|---|---|
| `tenant_admin` | 全部可见 | 非系统字段可编辑 | 敏感字段按授权可见 | 是 | 租户最高管理员 |
| `asset_admin` | 全部可见 | 非系统字段可编辑 | 敏感字段默认脱敏 | 是 | 资产台账全量 |
| `asset_operator` | 财务/合同类字段隐藏，其余可见 | 仅运维/运营字段可编辑 | 敏感字段脱敏 | 否（敏感） | `specified_location + specified_asset_type` |
| `asset_user` | 仅本人/责任人相关字段可见 | 不可编辑他人资产 | `license_key`/采购金额/合同附件隐藏 | 否 | `self/responsible` |
| `auditor` | 只读可见 | 不可编辑 | 敏感字段脱敏 | 否（敏感字段） | 只读审计 |

**默认规则服务解析顺序**：① 字段 `visible/editable/sensitive/encrypted/mask_rule` 基础属性 → ② 角色基线覆盖（上表）→ ③ 数据范围（detail/list 注入）→ ④ 输出：隐藏字段不出现在响应，敏感字段按 `mask_rule` 脱敏，`editable=false` 字段前端只读且后端拒写。

**敏感字段示例（MVP-1）**：`license_key`（软件许可证，脱敏为后 4 位+`***`）、采购金额类（无权限返回 `null`/`***`）。`license_key` 在软件许可证类型中定义为 `sensitive=true` 且 `encrypted=true`（见 M1-P1-01）。

---

## 6. UI 设计稿（MVP-1 需新增 9 个前端页面）

> 通用规范（引用 `16-UI设计规范与前端交互范式.md`）：企业级 B 端后台；左侧导航 + 顶部工具栏 + 主内容区 + 右侧抽屉；主色稳定蓝；所有页面必须有 **loading / empty / error / no-permission** 四种状态；动态表单/列表/筛选复用组件，不为每资产类型写死页面；字段权限在 UI 体现为 **隐藏 / 只读 / 脱敏**；危险操作二次确认。

### 6.1 资产类型管理（`metadata/asset-type`）
- **结构**：左侧资产类型树（可展开/选中/新增子节点）+ 右侧类型编辑表单（type_code/type_name/asset_kind/icon/sort_order/启用开关）。
- **状态**：树 loading 骨架；空树提示「新建第一个资产类型」；树加载失败 error 重试；无 `metadata:manage` 权限显示 no-permission。
- **驱动方式**：树由 `GET .../tree` 驱动；`type_code` 创建后不可编辑。
- **字段权限体现**：类型管理本身受功能权限 `metadata:manage` 控制（无权限整体隐藏入口）。

### 6.2 字段定义（`metadata/asset-type/:id/fields`）
- **结构**：选中类型后，字段表格（code/name/type/必填/唯一范围/敏感/加密/状态）+ 字段编辑抽屉（含 JSON 校验的 validation_rule/data_source）。
- **状态**：loading 表格；空字段提示「添加字段」；保存失败 error 提示（尤其唯一规则被拒的 `422`）；无权限 no-permission。
- **驱动方式**：`GET/POST/PUT/PATCH .../fields`；唯一范围选 `tenant/asset_type` 且字段非热点时，提交后被后端拒绝并提示「需升格为热点字段」。
- **字段权限体现**：本页受功能权限控制；字段级 `visible/editable` 不影响元数据配置页（元数据配置属于管理员能力）。

### 6.3 表单配置（`metadata/asset-type/:id/form`）
- **结构**：表单 schema 可视化编辑器（分组/列数/字段顺序拖拽）+ JSON 预览 + 发布按钮（发布前影响提示）。
- **状态**：loading；空配置提示；JSON 格式错误 error 内联提示；无权限 no-permission。
- **驱动方式**：`GET/PUT .../form-schema`；schema 与字段定义联动校验。
- **字段权限体现**：管理员配置，不受资产字段权限影响。

### 6.4 列表配置（`metadata/asset-type/:id/list`）
- **结构**：列表列配置（勾选列/宽度/固定列/默认排序）+ 预览。
- **状态**：同上四态；无权限 no-permission。
- **驱动方式**：`GET/PUT .../list-view`。
- **字段权限体现**：配置阶段可选列；运行时无权限字段不显示（由运行时接口+后端过滤保证）。

### 6.5 查询配置（`metadata/asset-type/:id/search`）
- **结构**：查询条件配置（字段/控件类型/枚举源/范围）+ 预览。
- **状态**：四态；无权限 no-permission。
- **驱动方式**：`GET/PUT .../search-schema`；控件类型与 `08` 字段类型映射。
- **字段权限体现**：管理员配置，不受资产字段权限影响。

### 6.6 资产列表（`asset/list`）
- **结构**（参考 `16` 7.1）：页面标题 + 操作栏（新增）+ 可折叠筛选区（类型/状态/组织/责任人/关键字）+ 资产表格（固定列：编号/名称/类型/状态/责任人/组织/更新时间/操作）+ 分页 + 右侧详情抽屉。
- **状态**：表格 loading；空数据空状态+「新增资产」入口；筛选/接口 error 提示；无 `asset:view` 权限 no-permission。
- **驱动方式**：列表列与筛选由 `GET .../runtime/{typeId}`（list_view + search_schema + 字段权限）驱动；数据由 `GET /api/v1/assets` 提供，自动注入租户与数据范围。
- **字段权限体现**：无 `visible` 权限的列不渲染；敏感列脱敏展示；操作列按功能权限裁剪。

### 6.7 资产详情（`asset/detail/:id`）
- **结构**（参考 `16` 7.2）：标题区（编号/名称/状态/主动作，但 MVP-1 无生命周期动作按钮，仅展示状态标签）+ 基础信息 + 动态字段分组 + 关系 Tab + （附件/审计 Tab 占位，MVP-1 仅关系可用）。
- **状态**：loading 骨架；资产不存在/无数据权限 → error 或 no-permission（404 映射）；空关系提示。
- **驱动方式**：`GET /api/v1/assets/{id}` + 运行时元数据；动态字段按 `attributes` 与物理列渲染。
- **字段权限体现**：响应已由后端过滤，`license_key` 等敏感字段脱敏/隐藏；`editable=false` 字段在「编辑」入口中只读。

### 6.8 资产新增/编辑（`asset/create`, `asset/edit/:id`）
- **结构**（动态表单，参考 `16` 8）：按运行时 schema 分组的动态表单（基础信息段 + 动态字段段）；必填标识；错误就近提示；底部固定操作栏（提交/取消）。
- **状态**：表单 loading（拉取运行时元数据）；提交中按钮 loading 防重复；校验失败 error 内联；无 `asset:create/asset:update` 权限 no-permission。
- **驱动方式**：`POST /api/v1/assets` 或 `PUT /api/v1/assets/{id}`；字段来自 `GET .../runtime/{typeId}`（字段定义 + form_schema + 字段权限 + 当前用户权限）。
- **字段权限体现**：`editable=false` 字段渲染为只读且提交时不传/被后端拒覆盖；`visible=false` 字段不渲染；敏感字段输入按 `mask_rule` 展示；**前端不传 `lifecycleStatus`，且即便传入也被忽略/拒绝**（后端权威）。

### 6.9 资产关系区域（`asset/detail/:id` 内 Tab）
- **结构**：关系表格（源/目标资产 + relation_type + 描述 + 操作）+ 「添加关系」抽屉（选目标资产 + 关系类型 + 描述）。
- **状态**：关系 loading；空关系空状态；保存失败 error（如非法 `relation_type` 的 `422`）；无 `asset:update` 权限时添加按钮禁用/隐藏（no-permission）。
- **驱动方式**：`GET/POST/DELETE .../relations`；目标资产选择器走 `GET /api/v1/assets` 同租户过滤。
- **字段权限体现**：关系区域受 `asset:update` 功能权限控制；关系数据本身按租户隔离。

---

## 7. 明确边界（MVP-1 不做，属于 MVP-2 及以后）

以下能力**不在本 PRD 范围**，架构师与工程师不得在本阶段实现：
- 生命周期状态机 / 动作 API / 守卫规则 / 生命周期事件日志（MVP-2）。
- 审批模板/节点/实例/任务，以及与生命周期动作的审批联动（MVP-3）。
- 软件许可证分配/回收/续费、及 `software_license`/`license_assignment` 专属表业务逻辑（MVP-2/MVP-4；MVP-1 仅以 asset 形式建模许可证）。
- 导入导出（Excel/CSV）、报表、通知、API Token、Webhook、附件上传下载（MVP-4/MVP-5）。
- 证书/域名/云资源轻量资产专属表业务与页面（MVP-4；本阶段不预置其元数据）。
- CMDB、自动发现、BPMN、生命周期动作、状态权限配置页面（MVP-3 及以后）。
- 字段权限**配置页面**（`field_permission_rules` 表预留，配置 UI 留 MVP-3）。

---

## 8. 待确认问题

本需求用户已非常明确，主要边界无歧义。但存在以下**隐含决策点**，供架构师/工程师注意，已附**建议方案**：

1. **`locations` 表是否本阶段必须建？**
   `assets.location_id` 为热点物理列且资产表单需「位置选择器」，但 `06` 未给出 `locations` 完整 DDL，9 个 UI 页面也不含位置管理页。
   **建议方案**：MVP-1 建立 `locations` 基础表（`id, tenant_id, parent_id, location_code, location_name, sort_order, ...`）+ 预置演示位置（如「总部机房-A01-10U」，见 `13`），提供位置选择器数据源；**不提供位置管理 UI**（留 MVP-2/3）。若主理人裁定 MVP-1 完全不建 `locations` 表，则 `location_id` 仅作自由文本/外部引用，需同步调整种子数据与表单。*需架构师确认采用哪种。*

2. **`lifecycle_template_id` 如何取初始状态？**
   资产创建时 `lifecycle_status` 默认「`planned` 或资产类型配置初始状态」，但生命周期模板/状态机属 MVP-2。
   **建议方案**：MVP-1 仍建立 `lifecycle_templates` + `lifecycle_states` 基础表（沿用 `06`），并预置 `planned` 为各类型起始状态；`asset_types.lifecycle_template_id` 可选绑定，资产创建时取模板起始状态，未绑定时硬编码 `planned`。生命周期动作 API 仍属 MVP-2。*需架构师确认是否 MVP-1 即预置生命周期基础表，还是仅用常量 `planned`。*

3. **网络设备 / 安全设备的预置字段清单未定义。**
   `08` 仅给出服务器、软件许可证、证书/域名、云资源的字段模板；MVP-1 预置四类含网络设备、安全设备，二者无现成字段模板。
   **建议方案**：参考服务器模板裁剪——网络设备增加 `management_ip`/`port_count`/`firmware_version`；安全设备增加 `firmware_version`/`rule_version`；二者均含 `brand/model/sn/warranty_end_date` 等热点字段。*需产品/架构师确认最终字段清单，或授权按服务器模板裁剪。*

4. **`field_type` 枚举不一致（06 vs 08）。**
   `06` 字段类型枚举为 `text/number/date/datetime/enum/user/org/location/boolean/file`；`08` 列出 `textarea/decimal/multi_enum/url/json/asset_relation` 等更细类型。
   **建议方案**：以 `06` 枚举为存储标准；`08` 中的 `textarea/decimal/multi_enum/url/json/asset_relation` 作为前端输入变体或扩展类型，渲染时映射，不扩大数据库枚举（避免 MVP-1 过度设计）。*需架构师确认是否扩展 06 枚举。*

5. **无歧义项确认**：热点字段白名单、部分唯一索引命名与条件、租户隔离后端解析、写操作审计、字段权限后端过滤、MVP-0 兼容约束——均已在第 3 节 P0 明确，**无需额外确认**。

---

> 文档结束。本 PRD 直接交付架构师作为 MVP-1 设计与实现依据；测试同学可直接将第 3 节验收要点映射至 `12` 的 M1/S/C 用例。
