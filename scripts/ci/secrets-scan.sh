#!/bin/bash
# ============================================================
# Secrets Scan（Phase 15/17）：扫描仓库已跟踪文件中的疑似秘密
# 退出码: 0=PASS 1=发现疑似秘密
# 说明: 白名单机制——dev-only 默认值/测试凭据/占位符在 allowlist 中排除
# ============================================================
set -uo pipefail
cd "$(dirname "$0")/../.."

FAIL=0
# 只扫描 Git 已跟踪文件（避免扫到本地 .env/构建产物）
FILES=$(git ls-files)

# 高危模式：赋值形式的真实 secret（排除占位符/dev 默认/env 引用/文档示例）
PATTERNS=(
  'JWT_SECRET=.[^$#{ ]'
  'MINIO_ROOT_PASSWORD=.[^$#{ ]'
  'MYSQL_ROOT_PASSWORD=.[^$#{ ]'
  'apikey.*=.*[a-zA-Z0-9]{20,}'
  'BEGIN (RSA |EC )?PRIVATE KEY'
)

ALLOWLIST=(
  '.env.example'
  'deploy/.env.example'
  'perf.properties'
  'application-dev.yml'
  'getting-started.md'
  'test-data.md'
  'security-testing.md'
  'phase15-gate.md'
  'ui-automation.md'
  'how-to-run-tests.md'
  'api-automation.md'
)

is_allowlisted() {
  for prefix in "${ALLOWLIST[@]}"; do
    [[ "$1" == *"$prefix"* ]] && return 0
  done
  return 1
}

echo "=== Secrets Scan（Git 已跟踪文件）==="
for f in $FILES; do
  skip=0
  for prefix in "${ALLOWLIST[@]}"; do
    [[ "$f" == *"$prefix"* ]] && skip=1 && break
  done
  [ "$skip" = "1" ] && continue

  for pat in "${PATTERNS[@]}"; do
    MATCHES=$(grep -nE "$pat" "$f" 2>/dev/null | grep -viE 'example|CHANGE_ME|dev-only|placeholder|<[^>]*>|\$\{' | head -3)
    if [ -n "$MATCHES" ]; then
      echo "  [疑似秘密] $f (pattern: $pat)"
      echo "$MATCHES" | head -3 | sed 's/^/    /'
      FAIL=1
    fi
  done
done

if [ "$FAIL" = "0" ]; then
  echo "=== Secrets Scan: PASS ==="
  exit 0
else
  echo "=== Secrets Scan: FAIL ==="
  exit 1
fi
