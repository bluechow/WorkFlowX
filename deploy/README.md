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
