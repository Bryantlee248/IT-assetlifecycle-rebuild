#!/usr/bin/env bash
#
# IT 资产生命周期管理系统 —— PostgreSQL 恢复脚本
# 用法（在仓库根目录执行）：  bash deploy/restore-postgres.sh <备份文件路径.sql.gz>
# 警告：此操作会覆盖现有数据库，且不可逆。
#
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "!! 用法错误：必须显式传入备份文件路径" >&2
  echo "   示例: bash deploy/restore-postgres.sh ./backups/itam-20250715-120000.sql.gz" >&2
  exit 1
fi

BACKUP_FILE="$1"

# 读取同目录 .env（若存在）以拿到 POSTGRES_DB / POSTGRES_USER
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

POSTGRES_DB="${POSTGRES_DB:-itam}"
POSTGRES_USER="${POSTGRES_USER:-itam}"

# 校验文件存在且非空
if [ ! -f "${BACKUP_FILE}" ]; then
  echo "!! 备份文件不存在: ${BACKUP_FILE}" >&2
  exit 1
fi

if [ ! -s "${BACKUP_FILE}" ]; then
  echo "!! 备份文件为空: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "⚠️ 即将覆盖数据库 ${POSTGRES_DB}，此操作不可逆，输入 YES 继续："
read -r CONFIRM
if [ "${CONFIRM}" != "YES" ]; then
  echo "!! 已取消恢复操作。"
  exit 1
fi

echo "==> 开始从 ${BACKUP_FILE} 恢复数据库 '${POSTGRES_DB}'..."
gunzip -c "${BACKUP_FILE}" | docker compose exec -T postgres psql -U "${POSTGRES_USER}" "${POSTGRES_DB}"
echo "==> 恢复完成。"
