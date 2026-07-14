# MVP-0 设计基线（平台底座）

> 本文件为 MVP-0 实现的绑定契约：范围边界、工程结构、数据库 schema、统一 API 契约、接口清单、认证与多租户上下文、权限与种子、审计、任务顺序、共享约定。
> 说明：原"架构师子代理"在本运行环境不可用（网络/代理 502），由主理人基于 docs/mvp 全量文档通读后产出此基线，作为工程师实现与 QA 验收的权威输入。技术栈严格遵循用户确认项，未做任何更改。

## 1. 范围边界

**实现（IN）**
- 后端：模块化单体（包即模块）、统一响应信封、统一错误码、OpenAPI/Swagger、全局异常处理、基础审计日志。
- 基础设施：PostgreSQL + Redis + Docker Compose + Flyway；基础表（见 §3）。
- 认证：JWT 登录/刷新(轮换)/退出/改密/must_change_password/当前用户/菜单/权限/可访问租户/租户切换；平台用户与租户用户上下文隔离。
- 管理：平台管理员建/列/启停租户；租户管理员管本租户组织/用户/角色权限。
- 健康检查：`GET /api/v1/health`（PG + Redis 状态）。
- 前端：登录、首次改密、主框架、租户切换、用户信息、菜单渲染、租户管理、组织树、用户管理、角色权限；四态（loading/empty/error/无权限）。

**不实现（OUT，严禁触碰）**：资产管理、资产类型、动态字段、生命周期状态机、审批、软件许可证、导入导出、报表、Webhook、API Token、附件、云资源、证书、域名；以及任何 MVP-1+ 能力（如字段权限引擎、状态权限矩阵表、资产 JSONB）。

## 2. 工程落盘与结构

- 后端：`D:/Codex Project/IT-assetlifecycle-rebuild/backend`（Maven 单模块，包即逻辑模块）
  - `com.itam.ItamApplication`
  - `com.itam.common`：result(ApiResponse/PageResult/ResultCode)、exception、util
  - `com.itam.config`：Swagger、Redis、Flyway、WebMvc
  - `com.itam.security`：JwtUtil、JwtFilter、SecurityConfig、CustomEntryPoint/AccessDenied、TenantContext/CurrentUser
  - `com.itam.auth`：登录/刷新/退出/改密/当前用户/菜单/权限/租户列表/租户切换
  - `com.itam.platform`：租户管理（平台管理员）
  - `com.itam.tenantadmin`：组织/用户/角色权限（租户管理员）
  - `com.itam.audit`：审计切面/服务/表
  - `com.itam.health`：健康检查
- 前端：`D:/Codex Project/IT-assetlifecycle-rebuild/frontend`（Vite + Vue3 + TS + Element Plus + Pinia + Vue Router）
  - `src/api`（axios 封装 + 各模块 client）、`src/store`（pinia：user/tenant/menu）、`src/router`、`src/views`、`src/components`、`src/utils`

## 3. 数据库 schema（MVP-0 表，PostgreSQL）

约定：主键 `id uuid default gen_random_uuid() primary key`；所有表含 `created_at timestamptz default now()`、`updated_at timestamptz default now()`、`deleted boolean not null default false`；平台级表**无** tenant_id，租户级表**有** `tenant_id uuid not null`；涉及操作人表含 `created_by uuid`、`updated_by uuid`（审计表除外，其本身即审计）。唯一约束统一用部分唯一索引 `... WHERE deleted = false`。

- **platform_user**（平台级）：username(varchar unique)、password_hash(varchar)、display_name(varchar)、email(varchar null)、phone(varchar null)、status(varchar 'ACTIVE'/'DISABLED')、must_change_password(boolean not null default false)。唯一：`username`（部分唯一）。
- **tenant**（平台级）：name(varchar)、code(varchar unique)、status(varchar 'ACTIVE'/'DISABLED')、description(varchar null)。唯一：`code`。
- **tenant_user**（租户级）：tenant_id、platform_user_id(uuid)、status(varchar)、role_id(uuid null)。唯一：`(tenant_id, platform_user_id)` 部分唯一。外键 tenant_id→tenant、platform_user_id→platform_user。
- **organization**（租户级）：tenant_id、parent_id(uuid null)、name(varchar)、code(varchar)、type(varchar)、sort(int)、status(varchar)。唯一：`(tenant_id, code)` 部分唯一。
- **role**（租户级）：tenant_id、code(varchar)、name(varchar)、description(varchar null)、is_system(boolean default false)。唯一：`(tenant_id, code)` 部分唯一。
- **permission**（平台级，权限目录）：code(varchar unique)、name(varchar)、module(varchar)、description(varchar null)。唯一：`code`。
- **role_permission**（租户级）：tenant_id、role_id(uuid)、permission_code(varchar)。唯一：`(tenant_id, role_id, permission_code)` 部分唯一。外键 role_id→role、permission_code→permission.code。
- **audit_log**（平台级，可记录平台与租户操作）：tenant_id(uuid null，平台操作留空)、actor_id(uuid)、actor_type(varchar 'PLATFORM'/'TENANT')、action(varchar)、biz_type(varchar)、biz_id(varchar null)、detail(jsonb null)、ip(varchar null)、created_at。无 updated_*/deleted（不可变）。索引：(tenant_id, created_at)、(actor_id, created_at)。

## 4. Flyway 迁移计划

- `V1__init_platform_schema.sql`：建表 + 索引 + 部分唯一索引 + 外键。
- `V2__seed_platform_data.sql`：permission 目录、平台管理员角色、内置租户管理员/租户用户角色模板、平台管理员用户（bcrypt 哈希）、一个演示租户 + 演示租户管理员（tenant_user 关联 + role 绑定）。
- 顺序执行；`spring.flyway.baseline-on-migrate=true`。
- 种子密码：`Platform@123`（平台管理员，must_change_password=false 以便直接验收）；演示租户管理员 `Tenant@123`（must_change_password=true 以演示首次改密）。

## 5. 统一 API 契约（MVP-0 基线，补齐 doc07 缺失）

- 统一响应体：`{ "code": 0, "message": "success", "data": <T>, "traceId": "<uuid>" }`。分页：`{ "code":0,"message":"success","data": { "page":1,"size":20,"total":100,"list":[...] }, "traceId":"..." }`。
- 错误码 `ResultCode`：SUCCESS(0,"成功")；PARAM_ERROR(40000,"参数错误")；UNAUTHENTICATED(40100,"未认证或登录已失效")；NO_PERMISSION(40300,"无权限")；NOT_FOUND(40400,"资源不存在")；CONFLICT(40900,"资源冲突")；BUSINESS_ERROR(50000,"业务错误")；REFRESH_INVALID(40101,"Refresh Token 无效或已轮换")。
- HTTP 语义：401→UNAUTHENTICATED/REFRESH_INVALID；403→NO_PERMISSION；404→NOT_FOUND；409→CONFLICT；400→PARAM_ERROR（含 Bean Validation）；500→BUSINESS_ERROR/系统异常。
- 鉴权头：`Authorization: Bearer <accessToken>`。时间格式 ISO-8601(UTC, `yyyy-MM-dd'T'HH:mm:ss'Z'`)，ID 用 UUID。
- 分页查询参数：`page`(从1)、`size`、`sort`(如 `createdAt,desc`)、过滤用平铺 query param。

## 6. 接口清单（前缀 /api/v1）

认证（无需认证除登出/刷新/改密/切换需 refresh 或 access）：
- `POST /auth/login` {username,password} → {accessToken,refreshToken,userType,mustChangePassword,tenantId?}
- `POST /auth/refresh` {refreshToken} → {accessToken,refreshToken}（轮换：旧 refresh 失效）
- `POST /auth/logout`（access） → 撤销 refresh
- `POST /auth/change-password`（access） {oldPassword,newPassword} → revoke all refresh
- `GET /auth/me`（access） → 当前用户
- `GET /auth/menu`（access） → 菜单树
- `GET /auth/permissions`（access） → 权限码列表
- `GET /auth/tenants`（access，租户用户） → 可访问租户列表
- `POST /auth/tenant-switch`（access，租户用户） {tenantId} → 新 token 对

平台管理（需 platform_admin + typ=PLATFORM）：
- `POST /platform/tenants` 建租户
- `GET /platform/tenants` 租户列表（分页）
- `POST /platform/tenants/{id}/disable` 停用
- `POST /platform/tenants/{id}/enable` 启用

租户管理（需 tenant 上下文 + 租户内权限）：
- `GET /tenant/organizations/tree` 组织树
- `POST /tenant/organizations` 建组织节点
- `PUT /tenant/organizations/{id}` 改组织
- `DELETE /tenant/organizations/{id}` 删组织
- `GET /tenant/users` 用户列表（分页，跨 tenant_user+platform_user）
- `POST /tenant/users` 新建租户用户（建 platform_user + tenant_user + 绑角色）
- `PUT /tenant/users/{id}` 改用户
- `DELETE /tenant/users/{id}` 停用/删
- `GET /tenant/roles` 角色列表
- `POST /tenant/roles` 建角色
- `PUT /tenant/roles/{id}` 改角色
- `DELETE /tenant/roles/{id}` 删角色
- `GET /tenant/roles/{id}/permissions` 角色权限
- `PUT /tenant/roles/{id}/permissions` 设置角色权限

健康检查：
- `GET /health` → {pg:"UP"/"DOWN", redis:"UP"/"DOWN"}

## 7. 认证与多租户上下文

- JWT Access（15min）/ Refresh（7d）。claims：sub=platform_user_id、typ=PLATFORM|TENANT、ten=tenantId(租户用户)、authorities=权限码列表。
- Refresh 轮换：Redis 存 `refresh:{refreshTokenJti}`→userId+tenantId，TTL=7d；刷新时校验存在并删除旧、签发新对（旧 JTI 加入吊销集），实现"用过的 refresh 不可再用"。
- 退出：删 Redis refresh + 加入 access 黑名单(短 TTL)。改密：删该用户所有 refresh + 黑名单其 access。
- 租户切换：校验 tenantId 在该用户可访问租户内，重新签发 token（ten 更新）。平台用户无 tenant 上下文，禁止切换。
- 上下文：`JwtFilter` 解析后写入 `SecurityContext` + `TenantContextHolder`（currentUserId/type/tenantId/permissions）。Controller 用 `@AuthenticationPrincipal` 或 `CurrentUser` 参数解析器获取。租户级 Repo 查询强制拼接 `tenant_id = currentTenantId AND deleted=false`（通过 JPA `@Where` 或基类 Criteria）。

## 8. 权限落地与种子

- 功能权限码（permission 目录种子）：`tenant:create`,`tenant:list`,`tenant:enable`,`tenant:disable`,`org:create`,`org:update`,`org:delete`,`org:list`,`user:create`,`user:update`,`user:delete`,`user:list`,`role:create`,`role:update`,`role:delete`,`role:assign`,`role:list`,`menu:view`,`profile:view`,`profile:update`。
- 内置角色：`platform_admin`（绑定全部平台权限，含 tenant:*）、`tenant_admin`（绑定除 tenant:* 外的全部租户内权限）、`tenant_user`（仅 profile/menu/user:list 视图权限）。
- 平台接口（`/platform/**`）要求 typ=PLATFORM 且具平台权限；租户接口要求 typ=TENANT。越界访问返回 403。租户隔离由 token 中 tenant_id 强制注入，前端不可伪造。

## 9. 审计

- `@Audit(action,bizType)` 注解 + AOP 切面，在关键写操作（建/启停租户、建/改/删组织、建/改/删用户、设角色权限、改密、登录失败）后写 audit_log。详情含前后摘要 JSONB。

## 10. 任务顺序（实现用）

T1 工程骨架+pom+配置 → T2 统一响应/异常/错误码 → T3 Flyway 基础表+种子 → T4 安全/JWT/上下文 → T5 认证接口 → T6 平台租户管理 → T7 租户内组织/用户/角色权限 → T8 审计切面 → T9 健康检查 → T10 Swagger → T11 后端测试(先写失败) → T12 前端骨架+布局 → T13 前端各页面 → T14 前端 API/状态管理 → T15 Docker Compose。

## 11. 共享约定与假设

- 包名 `com.itam`；服务端口 8080；上下文无前缀，路径含 `/api/v1`。
- Redis key 前缀 `itam:`；refresh token key `itam:refresh:{jti}`；access 黑名单 `itam:blacklist:{jti}`。
- bcrypt strength 10；JWT 密钥从环境变量 `ITAM_JWT_SECRET`（默认开发值，生产必改）。
- 分页默认 page=1,size=20,max=100。软删除查询统一 `deleted=false`。审计字段由切面/基类填充。
- 假设：MVP-0 租户内数据范围=本租户（tenant_id 隔离即满足），数据范围矩阵(self/own_org_tree)留待资产模块；字段权限引擎留 MVP-1。

## 12. 环境说明

本沙箱无 Java/Maven/Docker，无法本地编译运行；用户本地具备 JDK21+Maven+PostgreSQL+Redis 时可 `mvn test` 与 `docker compose up`。单测（JWT/密码/响应体/权限工具/WebMvc 切片）无需 DB 即可跑通。
