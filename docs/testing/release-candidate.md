# Release Candidate 验证（workflowx-release）

> P19-16~18：以独立 project name（workflowx-release）+ 全新 volumes 执行最终 fresh deployment、smoke 与四线回归。

## Round 1

### Fresh Deployment

见下方执行记录。等待 health 采用 compose service_healthy + curl 轮询（无固定 sleep）。

### Smoke

`bash scripts/deploy/smoke.sh` 15 断言。

### 四线回归

mvn / pytest / Vitest / Playwright（对 release 前端）。

## 结果

见 phase19-gate.md。
