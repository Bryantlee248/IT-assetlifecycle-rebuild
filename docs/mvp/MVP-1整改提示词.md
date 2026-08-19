> 注：本文档由同名 .docx 转换而来（docx 已从仓库移除，可由 tools/ 脚本重新生成）。

**MVP-1 整改提示词**

IT 资产全生命周期管理系统 · 元数据与资产核心复核整改

**使用方式：**将本文档中“正式提示词”完整发送给 AI
开发平台。不要拆分为多个不连续任务，除非平台有上下文长度限制；若必须拆分，应先发送“总体要求与验收标准”，再逐项发送
P0 整改项。

# 一、整改目标

| **类别** | **结论** | **要求** |
|----|----|----|
| 当前状态 | 工程构建通过，但 MVP-1 业务验收不通过 | 不得进入 MVP-2，先完成本次整改 |
| 整改重点 | 字段权限、数据范围、元数据驱动、关系约束 | 修复核心能力，不新增 MVP-2 功能 |
| 验收口径 | 自动化测试 + 构建 + 代码审查 + 可运行验证 | 所有 P0 项必须有测试覆盖 |

# 二、正式提示词

**请复制以下内容发送给 AI 开发平台：**

| 你现在接手 IT 资产全生命周期管理系统重建项目的 MVP-1 整改任务。当前代码位于项目根目录，技术栈固定为 Spring Boot 3 + Java 21 + PostgreSQL + Flyway + Vue 3 + TypeScript + Vite + Element Plus。请在现有 MVP-1 代码基础上做“外科手术式整改”，不要重构成新框架，不要进入 MVP-2 生命周期/审批/导入导出等后续功能。整改目标是让 MVP-1 的“元数据驱动 + 资产核心 + 字段权限基础能力”达到可验收状态。 |
|----|

# 三、必须遵守的工作原则

> **•** 先阅读
> docs/mvp/MVP1-PRD-许清楚.md、docs/mvp/MVP1-设计-高见远.md、docs/mvp/12-测试用例与验收标准.md、docs/mvp/16-UI设计规范与前端交互范式.md，再修改代码。
>
> **•** 只修复本提示词列出的 MVP-1 缺陷，不新增 MVP-2
> 能力，例如生命周期动作、审批流、导入导出、报表、许可证分配等。
>
> **•** 保持现有技术栈、目录结构、统一响应信封、权限模型、审计模型和
> Flyway 迁移体系，不引入不必要的新框架。
>
> **•** 每个 P0 修复项必须补充或更新自动化测试；不能只改前端或只改文档。
>
> **•** 不得通过删除测试、降低断言、跳过失败用例来制造通过。
>
> **•** 如涉及数据库结构变更，新增 Flyway V5 迁移，不修改已发布的 V1~V4
> 迁移文件，除非项目当前明确允许重放数据库。

# 四、P0 阻塞项整改要求

## P0-1 字段权限必须在后端写入路径强制执行

> **•** 问题：AssetAppService.update/create
> 当前直接接收并写入请求字段，没有基于运行时字段权限拒绝 editable=false
> 字段覆盖。
>
> **•**
> 要求：在资产创建/编辑前加载当前资产类型字段定义，调用字段权限服务解析当前角色对每个字段的权限。
>
> **•** 要求：visible=false 或 editable=false
> 的字段不得被写入；编辑时如果请求试图覆盖不可编辑字段，后端必须返回 403
> 或 422，不能静默覆盖。
>
> **•** 要求：固定物理字段和动态 attributes
> 字段都必须受同一套字段权限控制，尤其是
> serial_no、brand、vendor、license_key、lifecycle_status、status、created_at、updated_at
> 等。
>
> **•** 要求：lifecycleStatus 继续保持 MVP-1 受控策略：创建固定
> planned，编辑不得修改；如果请求携带 lifecycleStatus，必须忽略或返回
> 422，并补充测试证明。
>
> **•** 测试：新增/更新 AssetAppService 或 Controller 层测试，覆盖
> asset_admin 可编辑、auditor 不可编辑、editable=false
> 字段被拒绝、lifecycleStatus 无法被修改。

## P0-2 字段权限默认规则必须覆盖 5 类角色与数据范围基础规则

> **•** 问题：FieldPermissionService 当前只有简单角色基线，没有实现
> asset_user、asset_operator 的最小可见/数据范围语义。
>
> **•** 要求：至少实现
> tenant_admin、asset_admin、asset_operator、asset_user、auditor
> 五类角色的 MVP-1 默认规则。
>
> **•** 要求：auditor 只读，敏感字段脱敏；asset_user
> 默认只读且只能访问本人/责任人相关资产；asset_operator
> 仅可编辑运维/运营类字段，敏感字段脱敏；tenant_admin 与 asset_admin
> 按设计拥有管理能力但系统字段不可编辑。
>
> **•** 要求：资产列表和详情必须注入数据范围过滤，不能只按 tenant_id
> 过滤。最小实现可以基于
> owner_user_id、responsible_user_id、owner_org_id、location_id
> 等字段完成 MVP-1 范围的数据范围约束。
>
> **•** 要求：返回字段必须后端过滤，不可见字段不得出现在 API
> 响应中；敏感字段按 mask_rule
> 脱敏；加密字段不得明文落库或明文出现在审计 detail。
>
> **•** 测试：补充
> FieldPermissionServiceTest、AssetAppService/Repository
> 查询测试，覆盖五类角色、敏感字段脱敏、不可见字段剔除、asset_user
> 数据范围限制。

## P0-3 前端必须真正由运行时元数据和字段权限驱动

> **•** 问题：DynamicTable 期望 listView.columns 使用
> fieldCode/label，但 V4 种子 schema 使用
> field/title，导致配置列无法生效。DynamicFilter 期望
> enabledFilters，但种子 schema 使用 filters 数组，导致查询配置无法驱动
> UI。
>
> **•** 要求：统一前后端 schema 契约。可以选择调整前端兼容 field/title
> 与 filters 数组，也可以通过 V5 迁移修正种子 schema；推荐前端兼容旧
> schema，同时在后续种子中采用统一字段名。
>
> **•** 要求：DynamicForm 必须使用
> runtimeMetadata.fieldPermissions，而不是只使用字段定义
> f.editable。visible=false 字段不渲染，editable=false
> 字段只读，masked=true 字段按 FieldValue 脱敏展示。
>
> **•** 要求：固定字段区域也必须受 fieldPermissions
> 控制；不能无条件显示/可编辑
> assetName、assetNo、serialNo、brand、vendor 等字段。
>
> **•** 要求：AssetEditView 构造 payload
> 时不得提交不可见或不可编辑字段；但后端仍必须作为权威校验。
>
> **•**
> 测试：前端至少补充类型检查和关键组件测试，或提供可执行的手工验收说明；必须确保
> vue-tsc 与 vite build 通过。

## P0-4 资产关系类型必须受枚举约束

> **•** 问题：AssetRelationAppService 当前直接保存
> relationType，数据库也没有 check constraint。
>
> **•** 要求：定义允许的关系类型，例如
> installed_on、binds_to、depends_on、located_in、uses，非法值返回 422
> BUSINESS_RULE_VIOLATION。
>
> **•** 要求：禁止自环关系 sourceAssetId == targetAssetId。
>
> **•** 要求：继续保持源资产和目标资产同租户校验，跨租户返回 404 或
> 403。
>
> **•** 要求：如采用数据库约束，新增 V5 Flyway
> migration；如只在应用层实现，也必须有测试覆盖。
>
> **•** 测试：补充非法
> relationType、自环关系、跨租户关系、重复关系的测试。

## P0-5 审计与敏感信息保护必须补齐

> **•** 问题：当前审计 detail
> 较简略，且敏感字段加密/脱敏路径需要通过测试证明不会泄露明文。
>
> **•** 要求：资产创建/更新/删除、元数据变更、关系增删继续写审计。
>
> **•** 要求：审计 detail 中不得包含 license_key
> 等敏感字段明文；必要时记录字段名和变更摘要，不记录原始值。
>
> **•**
> 要求：加密字段应在落库前加密，响应前根据权限解密后脱敏或隐藏；无权限用户不得获得明文。
>
> **•** 测试：补充敏感字段落库非明文、API 响应脱敏、审计 detail
> 不含明文的测试。

# 五、P1 重要改进项

> **•** 修复前端构建大 chunk 警告：可通过路由级动态 import 或
> manualChunks 拆分 Element Plus/vendor 包。该项不阻塞
> MVP-1，但建议一起处理。
>
> **•** 清理无意义代码，例如 AssetSpecifications 中未使用的 ignore
> 变量。
>
> **•** 补充 API 契约说明，明确 listView/searchSchema/formSchema 的 JSON
> 结构，避免前后端再次漂移。
>
> **•**
> 补充本次整改报告，说明修改文件、测试覆盖、未完成风险和手工验证方式。

# 六、验收命令

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr>
<th>后端：<br />
cd backend<br />
mvn.cmd clean verify<br />
<br />
前端：<br />
cd frontend<br />
.\node_modules\.bin\vue-tsc.cmd --noEmit<br />
.\node_modules\.bin\vite.cmd build<br />
<br />
建议补充：<br />
1. 如本地具备 Docker/PostgreSQL，执行集成测试，确保 Flyway V1~V5
可从空库完整迁移。<br />
2. 启动后端与前端，使用
tenant_admin、asset_admin、auditor、asset_user、asset_operator
分别登录做手工验收。<br />
3. 验证软件许可证 license_key：数据库非明文、审计不泄露、auditor
响应脱敏、无权限用户隐藏或脱敏。</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 七、最终交付要求

> **•** 提交完整代码修改，不只提交说明文档。
>
> **•** 列出所有修改文件和每个文件的修改目的。
>
> **•** 列出新增/更新的测试用例及其覆盖的验收项。
>
> **•** 提供实际执行的命令输出摘要，包括后端 verify、前端
> typecheck/build、集成测试是否执行。
>
> **•**
> 明确说明是否仍有跳过测试；如果跳过，说明原因、环境要求和后续执行命令。
>
> **•** 整改完成后不要自动进入 MVP-2，等待复核确认。
