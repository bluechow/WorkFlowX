# deploy/ — 部署配置

存放部署相关配置（Nginx 配置、生产 compose、环境配置模板等）。

当前状态：Phase 1 无部署配置；本地开发基础设施使用仓库根目录 `compose.yaml`（ADR-007）。完整部署物在 Phase 16 产出。

## 已知环境事项：WSL2 空闲回收导致容器栈重启（2026-09-27）

**现象**：无任何 Windows 侧 WSL 会话时，发行版约 60 秒后被 WSL 优雅回收（服务按依赖顺序停止，
日志呈 systemd 有序 Stop 序列），dockerd 随之下次调用时重启，`restart: always` 的容器全部
重新拉起（数据卷不受影响，Flyway 不重复执行）。`.wslconfig` 的 `vmIdleTimeout` 对
「最后一个客户端会话关闭」触发的发行版回收不生效（systemd 发行版已知行为）。

**影响**：部署栈短暂不可达后会自动恢复健康；数据不丢。仅表现为间歇性连接拒绝。

**规避**（任选其一）：
1. 使用部署栈期间保持一个 WSL 终端/会话开启；
2. 登录时自启一个常驻会话：计划任务执行 `wsl.exe -e sleep infinity`；
3. 服务端常驻：将 compose 栈迁至不被回收的环境（裸机/云主机）。

数据安全：MySQL/Redis/MinIO 均使用命名卷，回收重启不丢失数据。

## 生产上线门禁（Final Edition P3 声明）

当前编排定位为**开发/演示级**。真实生产上线前必须补齐以下项（不与演示部署混用）：

| 门禁项 | 说明 |
|---|---|
| TLS 终结 | 前置反向代理（Nginx/LB）做 HTTPS，容器间流量不出内网 |
| Redis 认证 | `requirepass`/ACL，最小权限账号（当前开发栈无认证） |
| HTTP 安全头 | CSP / HSTS / X-Frame-Options 等在代理层配置 |
| 监控告警 | Actuator + Prometheus + AlertManager（JVM/DB/容器指标） |
| 日志集中化 | 容器 stdout → 集中采集（Loki/ELK），含 traceId 贯通 |
| 密钥管理 | JWT_SECRET/DB 密码入密钥管理服务，禁止 .env 明文入库 |
| 镜像不可变 | 按 digest 固定基础镜像；Actions 固定到版本并经 dependabot 升级 |
| 数据备份演练 | 按文档流程做一次真实恢复演练（而非仅备份） |

依赖供应链（P1 已落地部分）：CI `npm audit --audit-level=high`；dependabot 周检
maven/npm/docker/actions 四生态；MinIO/mc 已固定版本 tag。SBOM 与镜像扫描（Trivy 等）
列为后续项，在引入生产流量前接入。
