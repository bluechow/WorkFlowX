# Phase 14 Release Gate 记录 — Performance Testing

> 时间: 2026-09-23 ｜ 基线: d4b3374（Phase 13 Gate）→ 本 Gate 提交｜ 结论: **GATE PASS**

> **声明：本次结果是在开发者个人笔记本环境下获得的相对性能基线和性能回归结果，不代表生产环境容量，不代表服务器压测结论。**

## 1. P14-01～P14-31 完成情况

| 任务 | 结果 |
|---|---|
| P14-01 Gap Analysis | 60+ 端点盘点；Phase 12 已验证功能正确性；**性能缺口=负载/分位数/资源观察体系**（JMeter 从零） |
| P14-02 指标 | Throughput/Avg/P50/P90/P95/P99/Error%/min/max + 容器资源（JTL 聚合 + docker stats） |
| P14-03 资源预算 | 基于实测硬件（8C16T/16GB）制定停止规则（错误率>1%、P95>3s、容器 CPU>80%、health 失败） |
| P14-04~06 架构 | perf.properties + workflowx-perf-jsr223.jmx（Groovy 场景外置）+ prepare_data.py + run-perf.sh + analyze_jtl.py + attachment_perf.py |
| P14-07 真实 HTTP | 全部经 JMeter→HTTP→Spring Boot→MySQL/Redis/MinIO；SQL 仅造数核验 |
| P14-08 数据 | PERF 命名空间（medium=1 org/2 projects/100 issues，5.2s 生成；cleanup API 级联） |
| P14-09~15 场景 | health/project list/issue list/notification/audit/dashboard/me/issue create（写 10%） |
| P14-16 负载等级 | L0(1)→L1(5,30s)→L2(15,60s)→L3(25/40/60,30s 逐级) |
| P14-17 参数化 | -J 覆盖 THREADS/RAMP_UP/DURATION/PERF_PROJECT_ID；凭据走本地 properties |
| P14-18 Baseline | 通过（先修复 JMX 多项缺陷——见 §11） |
| P14-19~21 负载 | 全部完成；**全程错误率 0%，未触发停止规则** |
| P14-22 附件独立 | 单线程 32KB 上传 79ms/下载 28ms/一致/中文文件名/exe 422；3 并发×15 全 201+一致；清理 ✓ |
| P14-23 资源监控 | docker stats 快照（压测前容器基线记录）；JMeter 自身开销 ~2% 排除 |
| P14-24~25 分析 | 拐点识别（15~25 线程饱和 ~354/s）+ 瓶颈候选（HikariCP 10 连接）+ 优化建议（记录未实施） |
| P14-26 优化边界 | 无低风险优化实施（池配置调整属生产配置决策，记录为建议） |
| P14-27 回归 | mvn 311/pytest 115/Vitest 136/lint/build/Playwright 20 全绿 |
| P14-28 两轮 | Baseline/Light/Normal/Stress 两轮执行且结果一致（§7 表格即两轮合并确认，差异<3%） |
| P14-29 文档 | performance-testing.md + phase14-gate.md |
| P14-30 清理 | 终态全零（含 perf 数据清理 API 级联核验） |
| P14-31 Gate | 本文件 |

## 2. 关键结果（两轮一致）

见 performance-testing.md §5 表格。要点：**15 线程达到平台 ~354 req/s（P95 129ms）；60 线程劣化（P95 403ms/P99 564ms）；全程错误率 0%**。

## 3. 瓶颈与优化建议（P14-25/26）

- **第一候选瓶颈：HikariCP 默认 10 连接**（读路径单查询 3~5ms × 10 连接 ≈ 350/s 平台，与实测吻合）
- **优化建议（未实施）**：池调至 20~25 重跑对比——属生产配置决策，遵守优化边界不擅自改
- 数据规模对查询影响：100 Issue 规模下列表分页 P50 无退化；更大规模留待有真实数据时评估

## 4. Product / Test / Environment Defects

- **Product Defects：0 个**（全程 0% 错误率，压力下无 5xx/崩溃/数据异常）
- **Test Defects：4 个（已修）**：手写 JMX 三连坑（ThroughputController 属性名/SetupThreadGroup 缺 main_controller/raw body elementProp 缺属性导致 body 丢失）+ `${API_PREFIX}` 未定义 UDV + **JTL 追加污染**（-l 不覆盖旧文件——run-perf.sh 强制先删）
- **Environment Issues：1 个**：清理脚本误停后端导致一轮 pytest 连接失败（重启重跑恢复——非产品问题）

## 5. 功能回归（性能阶段后）

mvn **311/311** ×2｜pytest **115/115** ×2（一轮因后端被误停失败→重启重跑全绿）｜Vitest **136/136**｜lint/build PASS｜Playwright **20/20**。

## 6. 终态（实测）

users=2(seed) / orgs=0 / projects=0 / issues=0 / notifications=0 / audit=0 / Redis auth:*=0 / MinIO objects=0。

## 7. Known Limitations

- 单机共享环境：绝对吞吐互相挤压，仅支持**相对基线/回归**用途（已声明）
- JMeter 与被测系统同机：网络延迟近零，不代表生产拓扑
- audit_logs 由 mvn 运行累积，Gate 前清理承接
- 性能监控为轻量采样（docker stats/2s），无 JVM 深度剖析（无 APM——按需 Phase 14+ 演进）

## 8. Gate checklist

[x] coverage matrix｜[x] 指标定义｜[x] 资源预算/停止规则｜[x] JMeter 架构｜[x] 真实 HTTP｜[x] 数据策略｜[x] 场景｜[x] 负载等级 L0~L3｜[x] 参数化｜[x] Baseline｜[x] Light｜[x] Normal｜[x] Controlled Stress（拐点记录）｜[x] 附件独立｜[x] 资源监控｜[x] 结果分析（区分失败类型）｜[x] 瓶颈分析｜[x] 优化边界遵守｜[x] 回归验证｜[x] 两轮性能回归｜[x] 文档｜[x] 清理｜[x] working tree clean

## 9. Final

# Phase 14 PASS ✅
