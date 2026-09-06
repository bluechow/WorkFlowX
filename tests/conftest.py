"""共享 fixture 与环境配置（任务 1-7.3）。

Base URL 通过环境变量 WORKFLOWX_BASE_URL 覆盖，默认 http://localhost:8080。
"""
import os

import httpx
import pytest

BASE_URL = os.environ.get("WORKFLOWX_BASE_URL", "http://localhost:8080")


@pytest.fixture(scope="session")
def base_url() -> str:
    return BASE_URL


@pytest.fixture(scope="session")
def api_client(base_url: str):
    """面向后端的 HTTP 客户端（session 级复用）。"""
    with httpx.Client(base_url=base_url, timeout=10) as client:
        yield client
