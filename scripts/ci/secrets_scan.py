"""Secrets scan（Phase 15/17/18）。规则与 Phase 15 一致；性能稳定（单遍扫描）。"""
import re
import subprocess
import sys

FILES = subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()

PATTERNS = [
    (r"JWT_SECRET=.[^$#{ ]", "JWT secret"),
    (r"MINIO_ROOT_PASSWORD=.[^$#{ ]", "MinIO password"),
    (r"MYSQL_ROOT_PASSWORD=.[^$#{ ]", "MySQL root password"),
    (r"apikey.*=.*[a-zA-Z0-9]{20,}", "API key"),
    (r"BEGIN (RSA |EC )?PRIVATE KEY", "private key"),
]

# 白名单：占位模板/dev 文档/测试凭据文档（Phase 15 secrets scan 裁定 PASS 的范围）
ALLOW = re.compile(
    r"(\.env\.example|perf\.properties|application-dev\.yml|getting-started\.md|"
    r"test-data\.md|security-testing\.md|phase15-gate\.md|ui-automation\.md|"
    r"how-to-run-tests\.md|api-automation\.md|performance-testing\.md|ci-cd\.md|"
    r"secrets_scan\.py|backup\.sh|restore\.sh)"
)
PLACEHOLDER = re.compile(r"example|CHANGE_ME|dev-only|placeholder|<[^>]*>|\$\{")

fail = 0
for path in FILES:
    if ALLOW.search(path):
        continue
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            for lineno, line in enumerate(fh, 1):
                if PLACEHOLDER.search(line):
                    continue
                for pat, name in PATTERNS:
                    if re.search(pat, line):
                        print(f"  [疑似秘密] {path}:{lineno} ({name}): {line.strip()[:100]}")
                        fail = 1
    except OSError:
        continue

print("=== Secrets Scan: PASS ===" if not fail else "=== Secrets Scan: FAIL ===")
sys.exit(fail)
