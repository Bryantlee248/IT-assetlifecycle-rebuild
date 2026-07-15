#!/usr/bin/env bash
#
# IT 资产生命周期管理系统 —— PostgreSQL 备份脚本
# 用法（在仓库根目录执行）：  bash deploy/backup-postgres.sh
# 依赖：docker compose（脚本内部调用），备份写入 ./backups/itam-<时间戳>.sql.gz
#
set -euo pipefail

# 读取同目录 .env（若存在）以拿到 POSTGRES_DB / POSTGRES_USER
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

POSTGRES_DB="${POSTGRES_DB:-itam}"
POSTGRES_USER="${POSTGRES_USER:-itam}"

# 确保备份目录存在
mkdir -p ./backups

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="./backups/itam-${TIMESTAMP}.sql.gz"

echo "==> 开始备份数据库 '${POSTGRES_DB}' (用户 '${POSTGRES_USER}')"
echo "==> 备份将写入: ${BACKUP_FILE}"

# 通过 compose 内 postgres 执行 pg_dump，经 gzip 压缩落盘
docker compose exec -T postgres pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "${BACKUP_FILE}"

# 校验：文件必须存在且非空
if [ ! -s "${BACKUP_FILE}" ]; then
  echo "!! 备份失败：生成的文件为空或不存在 (${BACKUP_FILE})" >&2
  rm -f "${BACKUP_FILE}"
  exit 1
fi

echo "==> 备份成功: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

# 清理 14 天前的备份
echo "==> 清理 14 天前的备份..."
find ./backups -name '*.sql.gz' -mtime +14 -delete

echo "==> 完成。"
