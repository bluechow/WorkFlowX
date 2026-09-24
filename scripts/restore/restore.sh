#!/bin/bash
# ============================================================
# WorkFlowX 恢复（Phase 16; P16-26）
# 用法: bash scripts/restore/restore.sh <备份目录>
# ⚠ 覆盖目标库数据（restore 前自动做一次当前状态备份）
# 凭据: 从 deploy/.env 提取后内联传入（避免跨 shell 环境变量丢失）
# ============================================================
set -euo pipefail
SRC="${1:?用法: restore.sh <备份目录>}"
[ -f "$SRC/mysql-workflowx.sql" ] || { echo "FAIL: $SRC/mysql-workflowx.sql 不存在"; exit 1; }
cd "$(dirname "$0")/../.."
ROOT_PASS=$(grep '^MYSQL_ROOT_PASSWORD=' deploy/.env | cut -d= -f2-)
MINIO_USER=$(grep '^MINIO_ROOT_USER=' deploy/.env | cut -d= -f2-)
MINIO_PASS=$(grep '^MINIO_ROOT_PASSWORD=' deploy/.env | cut -d= -f2-)

echo "[0/3] 恢复前安全备份当前状态..."
bash scripts/backup/backup.sh "backups/pre-restore_$(date +%Y%m%d_%H%M%S)" >/dev/null

echo "[1/3] 恢复 MySQL..."
# P16 修正: Git Bash→wsl.exe 的 stdin 管道不稳定——改为 docker cp SQL 后容器内执行
MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- docker cp "$SRC/mysql-workflowx.sql" workflowx-deploy-mysql:/tmp/restore.sql
wsl -d Ubuntu -- docker exec workflowx-deploy-mysql sh -c "mysql -uroot -p'$ROOT_PASS' workflowx < /tmp/restore.sql"

echo "[2/3] 恢复 MinIO 对象..."
if [ -d "$SRC/minio" ]; then
  MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- docker cp "$SRC/minio" workflowx-deploy-minio:/tmp/minio-restore
  wsl -d Ubuntu -- docker exec workflowx-deploy-minio sh -c "mc alias set local http://localhost:9000 '$MINIO_USER' '$MINIO_PASS' >/dev/null && mc mirror --overwrite /tmp/minio-restore local/workflowx && rm -rf /tmp/minio-restore"
fi

echo "[3/3] RESTORE 完成 → $SRC"
