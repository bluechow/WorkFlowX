#!/bin/bash
# ============================================================
# WorkFlowX 恢复（Phase 16; P16-26）
# 用法: bash scripts/restore/restore.sh <备份目录>
# ⚠ 覆盖目标库数据；恢复前自动做一次当前状态备份
# ============================================================
set -euo pipefail
SRC="${1:?用法: restore.sh <备份目录>}"
[ -f "$SRC/mysql-workflowx.sql" ] || { echo "FAIL: $SRC/mysql-workflowx.sql 不存在"; exit 1; }
cd "$(dirname "$0")/../.."
ROOT_PASS=$(grep '^MYSQL_ROOT_PASSWORD=' deploy/.env | cut -d= -f2-)

echo "[0/3] 恢复前安全备份当前状态..."
bash scripts/backup/backup.sh "backups/pre-restore_$(date +%Y%m%d_%H%M%S)"

echo "[1/3] 恢复 MySQL..."
wsl -d Ubuntu -- docker exec -i workflowx-deploy-mysql sh -c "mysqldump... restore: mysql -uroot -p'$ROOT_PASS' workflowx" < "$SRC/mysql-workflowx.sql" 2>/dev/null || \
wsl -d Ubuntu -- docker exec -i workflowx-deploy-mysql sh -c "mysql -uroot -p'$ROOT_PASS' workflowx" < "$SRC/mysql-workflowx.sql"

echo "[2/3] 恢复 MinIO 对象..."
if [ -d "$SRC/minio" ]; then
  MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- docker cp "$SRC/minio" workflowx-deploy-minio:/tmp/minio-restore
  wsl -d Ubuntu -- docker exec workflowx-deploy-minio sh -c "mc mirror --overwrite /tmp/minio-restore local/workflowx && rm -rf /tmp/minio-restore"
fi

echo "[3/3] RESTORE 完成 → $SRC"
