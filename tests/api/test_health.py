"""健康检查 API 冒烟测试（任务 1-7.3）。

前置条件: 后端已启动（默认 http://localhost:8080，可用 WORKFLOWX_BASE_URL 覆盖）。
验证范围: 状态码 + 统一响应结构 + traceId 机制（Master Prompt §18，而非仅 HTTP 200）。
"""

import httpx


def test_health_returns_unified_structure(api_client: httpx.Client):
    resp = api_client.get("/api/v1/health")
    assert resp.status_code == 200

    body = resp.json()
    assert body["code"] == 200
    assert body["message"] == "success"
    assert body["data"]["status"] == "UP"
    assert body["data"]["service"] == "workflowx-backend"
    assert body["timestamp"]
    assert body["traceId"]
    assert resp.headers["X-Trace-Id"] == body["traceId"]


def test_unknown_api_returns_401_unified_structure(api_client: httpx.Client):
    """P2-05 起 anyRequest().authenticated()：未认证访问未知路径先被安全链拦截为 401（认证先于 404 判定，
    不向未认证者暴露资源存在性），这是安全架构的正确行为。"""
    resp = api_client.get("/api/v1/nonexistent")
    assert resp.status_code == 401

    body = resp.json()
    assert body["code"] == 401
    assert body["message"] == "authentication required"
    assert body["traceId"]
