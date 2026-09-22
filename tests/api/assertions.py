"""统一断言助手（Phase 12; P12-07）。

只封装有真实重复价值的断言；单次使用的仍用原生 assert + resp.json()。
所有断言失败时输出 endpoint/状态码/响应体摘要（脱敏由 client 层保证不打印 Authorization）。
"""
from __future__ import annotations

from typing import Any

import httpx


def _fail(resp: httpx.Response, expected: str, extra: str = "") -> AssertionError:
    body = ""
    try:
        body = resp.text[:400]
    except Exception:
        pass
    msg = f"[{resp.request.method} {resp.request.url}] 期望 {expected}，实际 {resp.status_code}；body={body}"
    if extra:
        msg += f"；{extra}"
    return AssertionError(msg)


def assert_success(resp: httpx.Response) -> dict:
    """2xx + Result envelope + 返回 data。"""
    assert resp.status_code < 300, _fail(resp, "2xx 成功")
    body = resp.json()
    assert isinstance(body, dict) and body.get("code") == 200, _fail(resp, "Result.code=200")
    assert "message" in body and "timestamp" in body and "traceId" in body, \
        _fail(resp, "Result envelope 含 message/timestamp/traceId")
    return body.get("data")


def assert_status(resp: httpx.Response, expected_status: int, expected_code: int | None = None) -> dict:
    """HTTP 状态码 + 业务 code 双重核对。"""
    assert resp.status_code == expected_status, _fail(resp, f"HTTP {expected_status}")
    body = resp.json()
    code = expected_code if expected_code is not None else expected_status
    assert body.get("code") == code, _fail(resp, f"Result.code={code}", f"实际 code={body.get('code')}")
    return body


def assert_error(resp: httpx.Response, expected_status: int) -> dict:
    """业务错误：HTTP 状态 + code + message 非空（不检查具体文案）。"""
    body = assert_status(resp, expected_status)
    assert body.get("message"), _fail(resp, "错误响应含 message")
    return body


def assert_page(body_or_resp: Any, min_total: int = 0, expect_list: bool = True) -> dict:
    """PageVO 结构契约：list/total/page/size 四字段齐全且类型正确。"""
    body = body_or_resp.json() if isinstance(body_or_resp, httpx.Response) else body_or_resp
    data = body.get("data") if isinstance(body, dict) and "data" in body else body
    assert isinstance(data, dict), f"PageVO 应为对象: {str(data)[:200]}"
    assert set(["list", "total", "page", "size"]).issubset(data.keys()), \
        f"PageVO 缺字段: {list(data.keys())}"
    assert isinstance(data["list"], list), "PageVO.list 应为数组"
    assert isinstance(data["total"], int), "PageVO.total 应为整数"
    if expect_list:
        assert data["total"] >= min_total, f"PageVO.total={data['total']} < 期望 {min_total}"
    return data


def assert_envelope(resp: httpx.Response, expected_code: int = 200) -> dict:
    """Result envelope 契约（P11 教训：包装层级必须显式核对）。

    安全层（Security handler）生成的 401/403 响应无 data 字段——业务层响应必须含 data。
    """
    body = resp.json()
    required = {"code", "message", "timestamp", "traceId"}
    if expected_code not in (401, 403):
        required = required | {"data"}
        assert "data" in body, f"Result envelope 缺 data: {list(body.keys())}"
    assert required.issubset(body.keys()), f"Result envelope 缺字段: {list(body.keys())}"
    assert body["code"] == expected_code, f"code={body['code']} 期望 {expected_code}"
    return body
