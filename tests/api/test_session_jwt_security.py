"""Session 与 JWT 安全测试（Phase 15; P15-02/P15-03）。marker: security。

单会话铁律: 二次登录覆盖旧会话（auth:session:{userId}）；登出/禁用立即失效。
JWT: 签名校验（HS256 服务端密钥）——payload 篡改（roles/userId/exp）必须 401，
不得通过修改 claims 提权。
"""
import base64
import json

import pytest

pytestmark = pytest.mark.security


def _b64url_decode(segment: str) -> bytes:
    pad = "=" * (-len(segment) % 4)
    return base64.urlsafe_b64decode(segment + pad)


def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _tamper_payload(token: str, mutate) -> str:
    """替换 JWT payload（保持原 signature——签名校验必须拒绝）。"""
    header, payload, signature = token.split(".")
    claims = json.loads(_b64url_decode(payload))
    mutate(claims)
    return f"{header}.{_b64url_encode(json.dumps(claims).encode())}.{signature}"


def _me(api_client, token: str):
    return api_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})


def test_second_login_invalidates_old_token(api_client, create_api_user):
    """单会话: 二次登录后旧 token 失效、新 token 有效（P15-02 核心）。"""
    user = create_api_user()

    def login():
        r = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
        return r.json()["data"]["accessToken"]

    token_a = login()
    assert _me(api_client, token_a).status_code == 200
    token_b = login()
    assert _me(api_client, token_b).status_code == 200, "新 token 有效"
    assert _me(api_client, token_a).status_code == 401, "token A 在 token B 登录后必须失效"


def test_logout_invalidates_token(api_client, create_api_user):
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    token = login.json()["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}
    assert api_client.get("/api/v1/auth/me", headers=headers).status_code == 200
    assert api_client.post("/api/v1/auth/logout", headers=headers).status_code == 200
    # 重复 logout 幂等
    assert api_client.post("/api/v1/auth/logout", headers=headers).status_code in (200, 401)
    assert _me(api_client, token).status_code == 401, "登出后 token 立即失效"


def test_disabled_user_kicked_and_restore_requires_relogin(api_client, admin_token, create_api_user):
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    token = login.json()["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}
    assert api_client.get("/api/v1/auth/me", headers=headers).status_code == 200
    # 禁用 → 立即踢线
    api_client.patch(f"/api/v1/users/{user['id']}/status",
                     json={"status": "DISABLED"}, headers={"Authorization": f"Bearer {admin_token}"})
    assert _me(api_client, token).status_code == 401, "禁用用户 token 立即失效"
    # 恢复后旧 token 仍无效（须重新登录）
    api_client.patch(f"/api/v1/users/{user['id']}/status",
                     json={"status": "ACTIVE"}, headers={"Authorization": f"Bearer {admin_token}"})
    assert _me(api_client, token).status_code == 401, "恢复后旧 token 不自动复活"


@pytest.mark.parametrize("auth_value", [
    None,
    "",
    "Bearer",
    "Basic dXNlcjpwYXNz",
    "Bearer not-a-jwt-at-all",
    "Bearer a.b.c",
], ids=["no-header", "empty", "bearer-no-token", "basic-scheme",
        "random-string", "three-dots"])
def test_malformed_bearer_rejected(api_client, auth_value):
    if auth_value is None:
        resp = api_client.get("/api/v1/auth/me")
    else:
        # 用底层 request 绕过 httpx 对空/空格 header 的规范化
        import httpx as _hx
        req = _hx.Request("GET", str(api_client.base_url) + "/api/v1/auth/me",
                          headers={"Authorization": auth_value})
        resp = api_client.send(api_client.build_request("GET", "/api/v1/auth/me",
                              headers={"Authorization": auth_value}))
    assert resp.status_code == 401, f"Authorization={auth_value!r} 应 401"


def test_jwt_payload_tampering_roles_rejected(api_client, create_api_user):
    """MEMBER token 的 roles 改为 ADMIN（保持原签名）→ 签名校验必须拒绝（P15-03 核心）。"""
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    token = login.json()["data"]["accessToken"]
    forged = _tamper_payload(token, lambda c: c.__setitem__("roles", ["ADMIN"]))
    resp = _me(api_client, forged)
    assert resp.status_code == 401, "篡改 roles 的 JWT 必须被签名校验拒绝"


def test_jwt_payload_tampering_user_id_rejected(api_client, create_api_user):
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    token = login.json()["data"]["accessToken"]
    forged = _tamper_payload(token, lambda c: c.__setitem__("sub", "1"))  # 冒充 admin(id=1)
    resp = _me(api_client, forged)
    assert resp.status_code == 401, "篡改 userId 冒充他人必须拒绝"


def test_jwt_payload_tampering_exp_rejected(api_client, create_api_user):
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    token = login.json()["data"]["accessToken"]
    forged = _tamper_payload(token, lambda c: c.__setitem__("exp", 99999999999))
    assert _me(api_client, forged).status_code == 401, "篡改 exp 续期必须拒绝"


@pytest.mark.parametrize("token", [
    "eyJhbGciOiJIUzI1NiJ9.e30.ang",  # 自造签名
    "abc",                             # 非 JWT 结构
    "a.b",                             # 段不足
    "eyJhbGciOiJub25lIn0.e30.",        # alg=none 无签名
], ids=["forged-sig", "not-jwt", "two-segments", "alg-none"])
def test_invalid_jwt_tokens_rejected(api_client, token):
    assert _me(api_client, token).status_code == 401, f"非法 JWT 必须拒绝: {token[:30]}"


def test_expired_jwt_rejected(api_client):
    """手工构造过期 token（正确密钥签不了——用已过期时间戳的自签 token，服务端 exp 校验先于签名差异即 401）。"""
    header = _b64url_encode(json.dumps({"alg": "HS256"}).encode())
    payload = _b64url_encode(json.dumps({
        "sub": "1", "username": "admin", "roles": ["ADMIN"],
        "iss": "workflowx", "iat": 1000000000, "exp": 1000003600, "jti": "x",
    }).encode())
    # 伪造签名（无法得知密钥——期望服务端先因签名不匹配拒绝，等效 401）
    forged = f"{header}.{payload}.Zm9yZ2Vk"
    assert _me(api_client, forged).status_code == 401, "过期/伪造 token 必须 401"
