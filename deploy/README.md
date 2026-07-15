# IT 资产生命周期管理系统 —— 第一版部署上线文档（MVP-0/1/2 平台底座）

本文档覆盖**第一版部署上线准备**，范围严格锁定已验证的 MVP-0/1/2（平台底座、元数据、资产关系、生命周期状态机）。
不包含任何 MVP-3（审批 / 通知 / 报表）内容。

部署形态：单台 Linux 服务器上以 `docker compose` 编排四服务 —— `postgres` / `redis` / `backend` / `frontend`。
数据与后端不暴露公网，前端仅绑定 `127.0.0.1`（默认 `http://127.0.0.1:3000`）。

---

## 1. 架构与端口

| 服务      | 镜像                  | 对内端口 | 对外端口           | 说明                                       |
|-----------|-----------------------|----------|--------------------|--------------------------------------------|
| postgres  | postgres:16           | 5432     | 不暴露             | 持久化数据卷 `itam-pg`                     |
| redis     | redis:7               | 6379     | 不暴露             | 缓存 / 会话                                |
| backend   | 本地构建（Spring Boot）| 8080     | 不暴露（仅内网）   | context-path `/api`，业务前缀 `/v1`        |
| frontend  | 本地构建（nginx）      | 80       | 127.0.0.1:3000     | 静态资源 + `/api` 反向代理到 backend       |

外部访问入口只有 `http://127.0.0.1:3000`：页面由 nginx 直接提供，`/api/**` 由 nginx 反代到 `backend:8080`。

---

## 2. 前置要求

- 服务器：Linux（x86_64），已安装 Docker Engine 与 Docker Compose v2（`docker compose` 子命令）。
- 本地构建机（CI）：JDK 21、Maven 3.9、Node 22（仅当你要在本地先 build 镜像推送时）。
- 本仓库根目录存在：`docker-compose.yml`、`.env.example`、`backend/`、`frontend/`、`deploy/`。

> 本机（开发机）**未安装 Docker**，因此下列构建/启动命令需在有 Docker 的环境中执行；开发机仅完成静态校验与单机构建验证。

---

## 3. 环境变量（`.env`）

复制模板并替换占位符：

```bash
cp .env.example .env
# 编辑 .env，至少替换：
#   POSTGRES_PASSWORD  -> 强随机串
#   ITAM_JWT_SECRET    -> 强随机串（>=32 字节）
```

生成 JWT 强随机串（任选其一）：

```bash
openssl rand -base64 48
# 或
python3 -c "import secrets;print(secrets.token_urlsafe(48))"
```

`ITAM_JWT_SECRET` 必须 >= 32 字节；若缺失，backend 在 `prod` profile 下启动会直接失败（`application-prod.yml` 中 `itam.jwt.secret: ${ITAM_JWT_SECRET}`，无默认值）。

`.env` 中的关键变量（compose 自动读取同目录 `.env`）：

| 变量                | 默认值     | 说明                                          |
|---------------------|------------|-----------------------------------------------|
| POSTGRES_DB         | itam       | 数据库名                                      |
| POSTGRES_USER       | itam       | 数据库用户                                    |
| POSTGRES_PASSWORD    | （必填）   | 数据库密码，缺失则 compose 报错退出           |
| ITAM_JWT_SECRET     | （必填）   | JWT 签名串，缺失则 backend 启动失败           |
| ITAM_PUBLIC_PORT    | 3000       | 前端对外绑定的 localhost 端口                 |
| ITAM_REDIS_PASSWORD | （空）     | Redis 密码，留空表示无密码                    |

---

## 4. 本地构建验证（在 Docker 不可用环境下也建议先跑）

后端（Spring Boot 测试 + 打包）：

```bash
cd backend
# Windows 需先设置 JAVA_HOME / PATH（见下方“开发机验证命令”）
mvn clean verify      # 期望 BUILD SUCCESS（约 135 测试 0 失败）
mvn -B package        # 产出 target/itam-backend-0.0.1.jar
```

前端（类型检查 + 构建）：

```bash
cd frontend
npm ci
npm run build         # = vue-tsc --noEmit && vite build，产出 dist/
```

---

## 5. 在本机（有 Docker 的服务器）启动

```bash
# 仓库根目录，确保已准备 .env
cd <repo-root>

# 构建并后台启动四服务
docker compose up -d --build

# 查看状态（等待所有服务 healthy）
docker compose ps

# 查看日志
docker compose logs -f backend
docker compose logs -f frontend
```

启动顺序由 `depends_on: condition: service_healthy` 保证：
`postgres`/`redis` 健康 → `backend` 启动并健康 → `frontend` 启动。

---

## 6. 常用运维命令

```bash
# 停止（保留数据卷）
docker compose stop

# 重启
docker compose restart

# 停止并移除容器（不删数据卷 itam-pg）
docker compose down

# 停止并移除容器 + 删除数据卷（危险！会清空数据库）
docker compose down -v

# 仅重新构建某服务（如改了代码）
docker compose up -d --build backend

# 滚动查看后端日志
docker compose logs -f --tail=100 backend
```

---

## 7. 数据库备份与恢复

脚本位于 `deploy/`，需在**仓库根目录**执行（脚本内部调用 `docker compose`，并读取 `.env`）。

备份（压缩写入 `./backups/itam-<时间戳>.sql.gz`，并自动清理 14 天前的备份）：

```bash
cd <repo-root>
bash deploy/backup-postgres.sh
```

恢复（**不可逆**，会覆盖现有数据库；需显式传入备份文件）：

```bash
cd <repo-root>
bash deploy/restore-postgres.sh ./backups/itam-20250715-120000.sql.gz
# 提示输入 YES 确认后开始恢复
```

> 脚本通过 `docker compose exec -T postgres pg_dump/psql` 操作容器内数据库，
> 因此数据库无需对外暴露端口。

---

## 8. 冒烟测试

`deploy/smoke-test.ps1`（PowerShell）对前端入口做端到端校验。
它只依赖外部可达的 `127.0.0.1:3000`，覆盖：首页外壳、SPA fallback、健康检查、登录、资产列表、生命周期链路。

```powershell
# 默认目标 http://127.0.0.1:3000
powershell -ExecutionPolicy Bypass -File deploy/smoke-test.ps1

# 带登录账号（推荐，可验证鉴权链路）
$env:ITAM_BASE_URL='http://127.0.0.1:3000'
$env:ITAM_SMOKE_USER='admin'
$env:ITAM_SMOKE_PASSWORD='<管理员密码>'
powershell -ExecutionPolicy Bypass -File deploy/smoke-test.ps1
```

未提供凭据时，脚本会跳过登录态相关断言（第 4/5/6 项）并告警，其余照跑；
任一项断言失败则 `exit 1`。

---

## 9. 常见故障排查

| 现象                                      | 可能原因 / 处理                                                  |
|-------------------------------------------|------------------------------------------------------------------|
| `ERROR: set POSTGRES_PASSWORD in .env`    | `.env` 缺失或未设置该变量；`cp .env.example .env` 并补全         |
| backend 容器反复重启 / 启动失败           | `ITAM_JWT_SECRET` 缺失或长度不足 32 字节；检查 `.env`             |
| `docker compose ps` 中 backend 一直 starting | 等待 postgres/redis healthy；查 `docker compose logs backend`    |
| 访问 `/api/...` 返回 404                   | nginx `proxy_pass` 误加结尾斜杠（已修正为 `http://backend:8080`）|
| 刷新 `/assets` 子路由 404                  | 缺少 SPA fallback（已配置 `try_files ... /index.html`）          |
| 前端能打开但登录 401                       | 检查账号权限是否含 `asset:view`；确认 JWT secret 前后端一致      |
| 数据库迁移失败（Flyway）                   | 检查 `validate-on-migrate`；不要手动改 `classpath:db/migration` |

---

## 10. 回滚方法

- **代码/镜像回滚**：保留上一版镜像 tag 或 git 上一提交，`docker compose down` 后重新
  `docker compose up -d --build`（使用旧源码/旧镜像）即可。
- **数据库回滚**：用最近一次成功备份恢复：
  ```bash
  bash deploy/restore-postgres.sh ./backups/<最近的备份>.sql.gz
  ```
  恢复前请确认备份时间点早于本次变更。

---

## 11. 安全与上线检查清单

详见 `docs/release/第一版部署上线检查清单.md`（逐项可勾选 + 对应命令）。

---

## 12. 严禁事项（范围锁定）

- 本版**不新增**任何业务功能，不进入 MVP-3（审批 / 通知 / 报表）。
- 不改动任何接口契约：`/api/v1/health`、`/api/v1/auth/login`、`/api/v1/assets`、`/api/v1/assets/{id}/lifecycle` 等。
- 不改动业务代码；仅允许部署相关文件（Dockerfile、nginx、compose、env 模板、prod 配置、脚本、文档）。
