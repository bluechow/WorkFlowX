# 测试运行说明

> 任务 1-7 ｜ 三层测试体系骨架（API 自动化体系在 Phase 12 完善）

## 0. 前置条件

- 基础设施已启动：`docker compose up -d`（仓库根目录）
- 后端已启动：`mvn -f backend/pom.xml spring-boot:run`（或 `java -jar backend/target/workflowx-backend-*.jar`）

## 1. 后端测试（单元 / Web 切片）

```bash
cd backend
mvn test
```

当前覆盖：统一响应结构、traceId 复用/回写（HealthControllerTest / ResultTest）。

## 2. 前端测试（组件冒烟）

```bash
cd frontend
npm run test      # Vitest（jsdom）
npm run lint      # ESLint 静态检查
npm run build     # 类型检查 + 产物构建
```

## 3. API 自动化（Python + Pytest，需后端运行中）

```bash
# 首次安装依赖（可追加 -i https://pypi.tuna.tsinghua.edu.cn/simple 加速）
pip install -r tests/requirements.txt

# 运行（默认 http://localhost:8080，可用环境变量覆盖）
pytest
# 或指定后端地址：
WORKFLOWX_BASE_URL=http://localhost:8080 pytest
```

当前覆盖：健康检查统一响应结构、404 错误结构（tests/api/test_health.py）。

## 4. 约定

- 测试之间相互独立（Master Prompt §21）；测试数据可重复生成（§20）
- 断言必须覆盖状态码 + 响应结构 + 业务结果，禁止只验证 HTTP 200（§18）
- Phase 12–14 将在 tests/ 下扩展 api/ui/performance 完整体系（ADR-004）
