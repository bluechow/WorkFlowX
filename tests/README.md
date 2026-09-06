# tests/ — 自动化测试体系（独立于业务代码）

测试代码独立于 Java/Vue 业务代码，遵守 Master Prompt §19 与 ADR-004。

| 目录 | 职责 | 引入阶段 |
|---|---|---|
| api/ | API 自动化（Python + Pytest + Requests/HTTPX） | Phase 1 骨架，Phase 12 完善 |
| ui/ | UI 自动化（Playwright） | Phase 13 |
| performance/ | 性能测试（JMeter） | Phase 14 |
| fixtures/ | 公共测试夹具 | Phase 1 骨架 |
| data/ | 测试数据与数据工厂 | Phase 1 骨架 |
| utils/ | 测试工具函数 | Phase 1 骨架 |
| config/ | 测试环境配置 | Phase 1 骨架 |

运行说明见 `docs/testing/how-to-run-tests.md`。
