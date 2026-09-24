#!/bin/bash
# ============================================================
# WorkFlowX 备份（Phase 16; P16-25）：MySQL dump + MinIO mirror
# 用法: bash scripts/backup/backup.sh [备份目录，默认 backups/<时间戳>]
# 前提: deploy 项目容器运行中；deploy/.env 存在；本机 docker 经 WSL 可用
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/../.."
TS=$(date +%Y%m%d_%H%M%S)
DEST="${1:-backups/$TS}"
mkdir -p "$DEST/minio"
WSL_ROOT="/mnt/d/codex协同项目/WorkFlowX"

# 凭据从 deploy/.env 读取（本地部署值，无 shell 特殊字符）
ROOT_PASS=$(grep '^MYSQL_ROOT_PASSWORD=' deploy/.env | cut -d= -f2-)
MINIO_USER=$(grep '^MINIO_ROOT_USER=' deploy/.env | cut -d= -f2-)
MINIO_PASS=$(grep '^MINIO_ROOT_PASSWORD=' deploy/.env | cut -d= -f2-)

echo "[1/3] MySQL dump..."
wsl -d Ubuntu -- docker exec workflowx-deploy-mysql sh -c "mysqldump -uroot -p'$ROOT_PASS' --single-transaction --routines --triggers workflowx" > "$DEST/mysql-workflowx.sql"
[ -s "$DEST/mysql-workflowx.sql" ] || { echo "FAIL: mysqldump 为空"; exit 1; }
echo "  dump 大小: $(wc -c < "$DEST/mysql-workflowx.sql") bytes"

echo "[2/3] MinIO mirror..."
wsl -d Ubuntu -- docker exec workflowx-deploy-minio sh -c "mc mirror --overwrite local/workflowx /tmp/minio-backup"
MSYS_NO_PATHCONV=1 wsl -d Ubuntu -- docker cp workflowx-deploy-minio:/tmp/minio-backup "$WSL_ROOT/$DEST/minio"
mkdir -p "$DEST/minio"
echo "  MinIO 备份文件数: $(find "$DEST/minio" -type f | wc -l)"

echo "[3/3] 元数据..."
cat > "$DEST/backup-meta.txt" <<META
backup_time: $(date '+%Y-%m-%d %H:%M:%S')
database: workflowx (schema+data, --single-transaction)
minio_bucket: workflowx (mc mirror)
compose_project: workflowx-deploy
META

echo "===== BACKUP 完成 → $DEST ====="
