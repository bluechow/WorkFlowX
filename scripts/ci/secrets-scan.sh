#!/bin/bash
# ============================================================
# Secrets Scan（Phase 15/17/18）：扫描 Git 已跟踪文件中的疑似秘密
# 实现: Python（性能稳定）；规则与 Phase 15 一致（白名单+模式）
# 退出码: 0=PASS 1=FAIL
# ============================================================
set -uo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/ci/secrets_scan.py 2>/dev/null || python scripts/ci/secrets_scan.py
exit $?
