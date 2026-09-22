"""统一 API Client（Phase 12; P12-03）。

基于 httpx.Client 的轻量会话封装：
- 独立 token（支持单会话策略下多用户并行）
- 统一 base_url / timeout / JSON 编解码
- 调试日志脱敏（绝不输出 Authorization/密码/JWT）
现有 conftest 的 api_client fixture 保持不变；新体系（factories/contract/business chain）使用本模块。
"""
from __future__ import annotations

import json
from typing import Any

import httpx

DEFAULT_TIMEOUT = 15.0


class ApiSession:
    """一个用户一个实例；token 注入到 Authorization 头，互不覆盖。"""

    def __init__(self, base_url: str, token: str | None = None, timeout: float = DEFAULT_TIMEOUT):
        self._client = httpx.Client(base_url=base_url, timeout=timeout)
        self.token = token
        if token:
            self._client.headers["Authorization"] = f"Bearer {token}"

    # ---- token 生命周期 ----
    def set_token(self, token: str | None) -> None:
        """替换 token（如角色变更后重登）；None=清除认证头。"""
        self.token = token
        if token:
            self._client.headers["Authorization"] = f"Bearer {token}"
        else:
            self._client.headers.pop("Authorization", None)

    def relogin(self, username: str, password: str) -> dict:
        """用本会话登录并注入新 token（覆盖的是本会话用户的会话）。返回 LoginResponse data。"""
        resp = self._client.post("/api/v1/auth/login",
                                 json={"username": username, "password": password})
        data = resp.json()["data"]
        self.set_token(data["accessToken"])
        return data

    # ---- HTTP 动词（返回 httpx.Response，断言层再解包）----
    def get(self, url: str, **kwargs: Any) -> httpx.Response:
        return self._client.get(url, **kwargs)

    def post(self, url: str, json: Any = None, **kwargs: Any) -> httpx.Response:
        return self._client.post(url, json=json, **kwargs)

    def put(self, url: str, json: Any = None, **kwargs: Any) -> httpx.Response:
        return self._client.put(url, json=json, **kwargs)

    def patch(self, url: str, json: Any = None, **kwargs: Any) -> httpx.Response:
        return self._client.patch(url, json=json, **kwargs)

    def delete(self, url: str, **kwargs: Any) -> httpx.Response:
        return self._client.delete(url, **kwargs)

    def upload(self, url: str, filename: str, content: bytes,
               content_type: str = "application/octet-stream") -> httpx.Response:
        return self._client.post(url, files={"file": (filename, content, content_type)})

    # ---- 资源管理 ----
    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "ApiSession":
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()


def redact_headers(headers: httpx.Headers | dict) -> dict:
    """调试用头信息脱敏：Authorization 一律掩码。"""
    out = {}
    for key, value in dict(headers).items():
        if key.lower() == "authorization":
            out[key] = "***"
        else:
            out[key] = value
    return out


def pretty_body(body: Any, limit: int = 400) -> str:
    """响应体摘要（失败定位用）；长度截断。"""
    try:
        return json.dumps(body, ensure_ascii=False)[:limit]
    except (TypeError, ValueError):
        return str(body)[:limit]
