# IT 资产全生命周期管理平台（第二代 · Rebuild）

> 新一代**多租户、元数据驱动、生命周期优先**的 IT 资产管理平台（ITAM）。
> 定位：**不是第一代系统的改写，而是重新设计的企业级平台**（第一版明确不实现旧系统历史数据迁移）。

## 与第一代系统的关系

| 仓库 | 定位 | 技术栈 | 状态 |
|---|---|---|---|
| [asset-lifecycle-manager](https://github.com/Bryantlee248/asset-lifecycle-manager) | 第一代生产系统（轻量咨询交付） | Python FastAPI + SQLite + Vue3 | 已上线运营，持续演进 |
| **本仓库（IT-assetlifecycle-rebuild）** | 第二代企业级重建 | Java 21 + Spring Boot 3 + PostgreSQL/Redis | MVP-0~3 已完成，MVP-4/5 规划中 |

> 两代产品并行开发：第一代服务现有生产需求，第二代按企业级标准重建（多租户、元数据驱动、生命周期状态机）。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.3.5 · DDD 分层（controller/application/domain/entity/repository/dto）· Flyway 数据库迁移 |
| 存储 | PostgreSQL（主库）· Redis（缓存） |
| 前端 | Vue3 + TypeScript + Vite + Element Plus + Pinia |
| 基础设施 | Docker Compose（postgres + redis + backend）· Testcontainers 集成测试 |
| 测试 | 34 个测试类（JWT 认证 / 租户隔离 / 生命周期守卫 / 审批流 / 字段权限 + 集成测试） |

## MVP 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| MVP-0 | 平台底座：多租户 / 认证 / 审计 / 健康检查 | ✅ 完成（验收 51/51 通过） |
| MVP-1/2 | 资产核心：资产元数据 / 生命周期状态机 | ✅ 完成 |
| MVP-3 | 审批流 + 通知 | ✅ 完成 |
| MVP-4/5 | 导入导出 / 报表、生产加固 | ⏳ 规划中 |

> 详细设计见 `docs/mvp/`（17 份编号文档：PRD / 用户故事 / 模块设计 / 数据模型 / API 规格 / 测试用例 / 权限 / 状态流转 / 开发提示词 / 架构评审）。

## 快速开始

```bash
# 启动 postgres + redis + backend
docker compose up -d

# 后端测试
cd backend && mvn test

# 前端开发
cd frontend && npm install && npm run dev
```

## CI

GitHub Actions 自动执行：后端 `mvn test` + 前端 `npm run build` + `docker compose config` 校验（见 `.github/workflows/ci.yml`）。

## 文档索引

- `docs/mvp/`：MVP 0-3 设计基线 / 验收报告 / 整改报告 / 开发与整改提示词（md）
- `docs/mvp3/`：ARCHITECTURE.md + PRD.md
- `docs/handoff/`、`docs/standards/`：交接与规范
- `tools/`：提示词文档生成脚本

## 许可证

[MIT](LICENSE) © 2026 Bryantlee248
