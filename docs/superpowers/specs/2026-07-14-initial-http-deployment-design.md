# IT Asset Lifecycle MVP-1 初始 HTTP 部署设计

## 1. 目标与范围

本设计用于将 GitHub 已提交的 MVP-1 应用部署到 `125.77.25.229`，通过公网 IP 提供 HTTP 访问，并完成数据库迁移、登录和资产 CRUD 冒烟验证。

部署基线固定为提交 `b86515d`。当前工作区中尚未提交的 MVP-2 生命周期代码不进入本次部署，也不得被覆盖、暂存或提交。

本次包含：

- Docker Compose 四服务部署；
- 容器 PostgreSQL 与 Redis；
- Spring Boot 后端；
- Vue 静态前端及容器内 Nginx；
- 宿主机 Nginx 公网入口；
- 环境变量、随机密钥、日志轮转和数据库备份；
- 健康检查、登录和资产 CRUD 冒烟测试。

本次不包含域名和 HTTPS。域名解析到服务器后再单独配置证书和 HTTP 到 HTTPS 跳转。

## 2. 部署架构

请求链路：

```text
公网 :80
   |
宿主机 Nginx
   |
127.0.0.1:3000
   |
前端 Nginx 容器
   |-- /        Vue SPA 与 history fallback
   `-- /api/*   反向代理到 backend:8080
                         |
                   Spring Boot 后端
                      |        |
                 PostgreSQL  Redis
```

网络边界：

- 只有宿主机 Nginx 对公网监听 `80`；
- 前端容器仅发布到宿主机 `127.0.0.1:3000`；
- 后端、PostgreSQL 和 Redis 不发布宿主机端口；
- 四个容器通过 Compose 内部网络通信；
- PostgreSQL 使用独立命名卷持久化；
- 服务器现有宿主机 PostgreSQL 保持运行，不参与本应用部署。

## 3. 项目文件

实施阶段新增或修改以下文件：

- `frontend/Dockerfile`：Node 构建阶段与 Nginx 运行阶段；
- `frontend/nginx.conf`：SPA 回退、静态资源缓存和 `/api` 代理；
- `frontend/.dockerignore`：排除依赖和构建产物；
- `docker-compose.yml`：PostgreSQL、Redis、后端和前端完整编排；
- `.env.example`：无敏感值的部署变量模板；
- `deploy/backup-postgres.sh`：PostgreSQL 备份与保留策略；
- `deploy/restore-postgres.sh`：显式恢复脚本；
- `deploy/README.md`：部署、验证、备份和恢复命令。

宿主机文件：

- `/opt/itam/app`：部署代码；
- `/opt/itam/.env`：真实环境变量，权限 `600`；
- `/opt/itam/backups`：数据库备份，权限仅限 root；
- `/root/.config/itam/admin-credentials`：随机管理员密码，权限 `600`；
- `/etc/nginx/sites-available/itam`：公网 IP HTTP 网关配置；
- `/etc/nginx/sites-enabled/itam`：启用链接。

## 4. 配置与密钥

`.env.example` 只定义变量名、格式和说明。真实 `.env` 在服务器生成，不进入 Git。

至少包含：

- `POSTGRES_DB`；
- `POSTGRES_USER`；
- `POSTGRES_PASSWORD`；
- `ITAM_JWT_SECRET`；
- `ITAM_PUBLIC_PORT=3000`。

`POSTGRES_PASSWORD` 和 `ITAM_JWT_SECRET` 使用服务器安全随机源生成。Compose 使用 `${VAR:?message}` 强制必填变量，缺失时拒绝启动。

前端端口必须按 `127.0.0.1:${ITAM_PUBLIC_PORT}:80` 发布，不允许绑定到 `0.0.0.0`。

生产运行通过环境变量覆盖开发默认值：

- 应用日志级别为 `INFO`；
- Swagger UI 和 API 文档关闭；
- Actuator 健康详情隐藏；
- 数据库与 Redis 只通过内部服务名连接。

首次部署使用种子账号完成登录测试后，立即将公开代码中的平台管理员和租户管理员演示密码替换为随机密码。新密码保存到 `/root/.config/itam/admin-credentials`，权限为 `600`，不写入项目目录和聊天记录。

## 5. 容器健康与启动顺序

- PostgreSQL 使用 `pg_isready` 健康检查；
- Redis 使用 `redis-cli ping` 健康检查；
- 后端使用 `/api/v1/health` 健康检查；
- 前端使用本地 HTTP 首页健康检查；
- 后端等待 PostgreSQL 和 Redis 健康；
- 前端等待后端健康；
- 所有服务使用 `restart: unless-stopped`。

部署过程先执行 Compose 配置校验，再构建和启动。任何健康检查失败都会停止后续冒烟测试，并保留容器状态和日志用于诊断。

## 6. 日志策略

所有容器使用 Docker `json-file` 日志驱动：

- 单个日志文件最大 `10m`；
- 最多保留 `5` 个文件。

Spring Boot 默认使用 `INFO` 级别。宿主机 Nginx 使用 Ubuntu 已有的 logrotate 机制管理访问日志和错误日志。

## 7. 数据备份与恢复

PostgreSQL 数据保存在命名卷中。每天执行一次 `pg_dump` 自定义格式备份，备份写入 `/opt/itam/backups`，保留最近 14 天。

备份脚本必须：

- 使用 Compose 内部 PostgreSQL 服务；
- 先写临时文件，成功后原子重命名；
- 对空文件或失败退出码返回失败；
- 删除超过 14 天的旧备份。

恢复脚本必须显式接收备份文件路径，并在执行前验证文件存在。恢复不会加入自动任务，避免误覆盖数据库。

定时任务由 root 的 cron 配置，每日 `02:30`（Asia/Shanghai）运行。首次部署必须手动执行一次备份并验证文件非空。

## 8. 部署与回滚

初次部署步骤：

1. 从提交 `b86515d` 创建独立 Git worktree 和部署分支，隔离当前 MVP-2 工作区；
2. 生成服务器 `.env` 和管理员凭据文件；
3. 校验 Compose 配置；
4. 构建后端和前端镜像；
5. 启动 PostgreSQL、Redis、后端和前端；
6. 等待全部服务健康；
7. 校验并重载宿主机 Nginx；
8. 执行冒烟测试；
9. 执行首次数据库备份。

配置文件变更前保留服务器原文件备份。应用回滚使用上一提交重新构建并启动，数据库卷不删除。涉及数据库迁移的回滚必须先备份，禁止自动执行破坏性降级。

## 9. 验收标准

以下条件全部满足才视为初始 HTTP 部署成功：

1. `docker compose ps` 显示四个服务运行且健康；
2. Flyway `V1` 至 `V5` 迁移成功；
3. 公网 IP `/` 返回前端 SPA；
4. 前端子路由刷新仍返回 SPA；
5. `/api/v1/health` 返回 PostgreSQL 和 Redis 均为 `UP`；
6. 平台和租户账号登录成功，首次改密流程成功；
7. 资产类型查询、资产创建、列表、详情、更新和删除全部成功；
8. 重启应用容器后数据和新管理员密码仍有效；
9. 手动备份生成非空备份文件；
10. 公网仅开放 `80`，`3000`、`8080`、`5432` 和 `6379` 不可直接访问；
11. Nginx 和容器日志没有持续错误。

## 10. 外部前置项

HTTPS 需要一个解析到 `125.77.25.229` 的域名。域名可用后，再使用受信任证书配置 `443`、证书自动续期和 HTTP 到 HTTPS 跳转。
