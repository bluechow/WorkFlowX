# 性能测试体系（Phase 14）

## 1. 测试目标

在开发者个人笔记本环境下建立 WorkFlowX 的**相对性能基线、负载/受控压力回归与瓶颈观察能力**。

> **声明：本次结果是在开发者个人笔记本环境下获得的相对性能基线和性能回归结果，不代表生产环境容量，不代表服务器压测结论。**

## 2. 环境与硬件（实测记录）

| 项 | 值 |
|---|---|
| CPU | AMD Ryzen 7 6800H（8 核 16 线程） |
| 内存 | 16GB（WSL 分配 7.38GB） |
| OS | Windows + WSL2 Ubuntu（Docker 容器内跑 MySQL/Redis/MinIO） |
| JMeter | 5.6.3（D:\apache-jmeter-5.6.3） |
| Java | OpenJDK 21.0.12.1 LTS |
| 后端 | Spring Boot（javaw jar 运行 :8080），**JVM 堆未显式设置（默认）** |
| HikariCP | **Spring Boot 默认（maximumPoolSize=10）** |
| 容器基线资源 | Redis 11MiB / MySQL 659MiB / MinIO 118MiB（压测前） |

## 3. 架构

```
tests/performance/
├── jmeter/workflowx-perf-jsr223.jmx   # JSR223Sampler(Groovy) 驱动的混合场景
├── jmeter/mixed-scenario.groovy       # 场景逻辑（轮盘选择/认证懒加载/SampleResult 填充）
├── perf.properties                    # 参数（BASE_URL/HOST/PORT/THREADS/凭据——本机 dev 明文）
├── scripts/prepare_data.py            # 数据准备（small/medium）与清理（API+SQL 终态核验）
├── scripts/run-perf.sh                # 场景运行器（先删 JTL 防追加污染）
├── scripts/analyze_jtl.py             # JTL 聚合（reqs/err%/throughput/avg/P50/90/95/99/min/max）
├── scripts/attachment_perf.py         # 附件独立测试
└── results/                           # JTL/log（gitignore）
```

**真实 HTTP 原则**: JMeter(Groovy HttpURLConnection) → HTTP → Spring Boot → MySQL/Redis/MinIO。零 mock、零直连 Service/DB 作为被测路径；SQL 仅用于造数核验与终态清理。

## 4. 场景与比例（mixed-scenario.groovy 轮盘）

health 5% / project list 20% / issue list 20% / notification list 10% / audit list 10% / dashboard 10% / me 10% / **issue create 10%（唯一写，保守）**。Issue 状态流转不进混合压测（状态机 409 会污染错误率语义），由 Phase 12 API 并发用例覆盖。

## 5. 负载等级与结果（中等数据集：1 组织/2 项目/100 Issue）

| 级别 | 线程 | 时长 | 请求数 | 错误率 | Throughput | Avg | P95 | P99 |
|---|---|---|---|---|---|---|---|---|
| L0 Baseline | 1 | 30s | 2,558 | 0% | 85.3/s | 12ms | 22ms | 24ms |
| L1 Light | 5 | 30s | 9,315 | 0% | 310.6/s | 15ms | 37ms | 49ms |
| L2 Normal | 15 | 60s | 21,227 | 0% | 353.8/s | 39ms | 129ms | 151ms |
| L3 Stress-25 | 25 | 30s | 10,204 | 0% | 340.2/s | 62ms | 173ms | 196ms |
| L3 Stress-40 | 40 | 30s | 9,992 | 0% | 333.1/s | 91ms | 303ms | 359ms |
| L3 Stress-60 | 60 | 30s | 9,704 | 0% | 323.5/s | 125ms | 403ms | 564ms |

**拐点分析**: 吞吐在 15 线程达到平台 **~354/s**；40 线程起吞吐不再上升而 P95 翻倍（排队），60 线程吞吐回落（323/s）+ P99 564ms——**饱和点在 15~25 线程之间，第一候选瓶颈为 HikariCP 默认 10 连接**（读路径 avg 单查询 3~5ms，10 连接 × 串行 ≈ 350/s 与实测吻合）。**全程错误率 0%**——失败语义下系统未崩溃，仅排队延迟上升。

## 6. 附件独立测试（与混合压测分离）

单线程：32KB 中文文件名上传 79ms / 下载 28ms / 逐字节一致 ✓ / 非法类型 exe→422 ✓。
低并发：3 线程 × 15 个 8KB 文件全部 201+一致 ✓（avg 93ms）。
清理：附件 MinIO+DB 双端删除 + 组织级联 ✓。

## 7. 资源保护阈值与停止规则（P14-03）

错误率>1%、P95>3000ms、后端 /health 失败、容器 CPU 持续>80%、系统卡顿——本轮未触发任何停止规则（全程错误率 0%）。JMeter 自身开销：1 个 jmeter 进程 ~2%（client-side 不是瓶颈的证据：L0 1 线程 P95 22ms 与 15 线程 P50 20ms 量级连续）。

## 8. 已知限制

- 单笔记本共享环境：JMeter 与被测系统同机，绝对吞吐被互相挤压——**只用于相对基线与回归对比**
- audit_logs 会随测试运行累积（Java/性能测试的 seed 账号审计）——Gate 前清理
- HikariCP=10 为 Spring Boot 默认——**优化建议**（未实施，避免与生产推荐值偏离）：若以吞吐为目标可将池调至 20~25 并重跑 L2/L3 对比
- 未测：MinIO 大文件、WebSocket（不存在）、登录高频（限流语义独立，由 Phase 12 覆盖）

## 9. 运行方式

```
cd tests/performance
python scripts/prepare_data.py --scale medium          # 造数（记录 PERF_PROJECT_ID）
PERF_PROJECT_ID=4502 ./scripts/run-perf.sh level2-normal 15 10 60
python scripts/analyze_jtl.py results/level2-normal.jtl
python scripts/prepare_data.py --cleanup               # 清理
```
