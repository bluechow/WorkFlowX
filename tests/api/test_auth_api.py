"""Auth API 黑盒测试（P2-20）：真实 HTTP 请求。

覆盖: 登录成功载荷 / 统一错误 / /me / 登出失效。
断言原则: 状态码 + 响应结构（code/message/data）+ 必要字段 + 敏感字段排除（Master Prompt §18）。
"""

import httpx

from conftest import PASSWORD, auth_headers, login

SENSITIVE_MARKERS = ("password", "password_hash", "passwordHash")


def assert_no_sensitive_fields(body: str):
    for marker in SENSITIVE_MARKERS:
        assert marker not in body, f"响应不得包含敏感字段 {marker}: {body}"


def test_login_success_returns_complete_payload(api_client: httpx.Client, unique_suffix, create_api_user):
    # 使用工厂用户验证登录载荷（admin 账号保留给 admin_token fixture，避免单会话互相顶掉）
    user = create_api_user(unique_suffix)
    data = login(api_client, user["username"], PASSWORD)
    assert data["accessToken"]
    assert data["tokenType"] == "Bearer"
    assert data["expiresIn"] == 7200
    assert data["userId"] == user["id"]
    assert data["username"] == user["username"]
    assert isinstance(data["roles"], list)


def test_login_response_does_not_leak_password(api_client: httpx.Client, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix + "leak")
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": PASSWORD})
    assert resp.status_code == 200
    assert_no_sensitive_fields(resp.text)


def test_login_with_wrong_password_returns_unified_401(api_client: httpx.Client, unique_suffix, create_api_user):
    # 使用工厂用户: 避免对 admin 的失败登录累积 auth:fail:admin 导致管理员被锁（ADR-010）
    user = create_api_user(unique_suffix + "wrong")
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": "WrongPass@999"})
    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == 401
    assert body["message"] == "用户名或密码错误"
    assert body["traceId"]


def test_login_with_nonexistent_user_returns_unified_401(api_client: httpx.Client):
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": "api_test_no_such_user", "password": "Whatever@123"})
    assert resp.status_code == 401
    body = resp.json()
    assert body["message"] == "用户名或密码错误"


def test_me_returns_current_database_user(api_client: httpx.Client, admin_token):
    resp = api_client.get("/api/v1/auth/me", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 200
    data = body["data"]
    assert data["username"] == "admin"
    assert data["email"] == "admin@workflowx.local"
    assert data["status"] == "ACTIVE"
    assert_no_sensitive_fields(resp.text)


def test_me_without_token_returns_401(api_client: httpx.Client):
    resp = api_client.get("/api/v1/auth/me")
    assert resp.status_code == 401
    assert resp.json()["code"] == 401


def test_logout_deletes_session_and_invalidates_token(api_client: httpx.Client, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    token = login(api_client, user["username"], PASSWORD)["accessToken"]
    headers = auth_headers(token)

    resp = api_client.post("/api/v1/auth/logout", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["code"] == 200

    # 登出后原 token 立即失效
    resp = api_client.get("/api/v1/auth/me", headers=headers)
    assert resp.status_code == 401
    resp = api_client.post("/api/v1/auth/logout", headers=headers)
    assert resp.status_code == 401
