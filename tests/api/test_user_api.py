"""User API 黑盒测试（P2-20）：权限矩阵 + CRUD + 禁用踢线，全部真实 HTTP。

权限语义: 未认证 401 / MEMBER 403 / ADMIN 200·201；响应不含 password/passwordHash。
测试数据: api_test_ 前缀用户经 ADMIN API 创建，会话结束统一清理（conftest cleanup）。
"""

import httpx

from conftest import PASSWORD, USER_PREFIX, auth_headers

SENSITIVE_MARKERS = ("password", "password_hash", "passwordHash")


def assert_no_sensitive_fields(body: str):
    for marker in SENSITIVE_MARKERS:
        assert marker not in body, f"响应不得包含敏感字段 {marker}: {body}"


def test_admin_can_list_users(api_client: httpx.Client, admin_token):
    resp = api_client.get("/api/v1/users", params={"page": 1, "size": 10},
                          headers=auth_headers(admin_token))
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 200
    assert body["data"]["total"] >= 2, "应包含 seed 用户 admin/user1"
    assert body["data"]["page"] == 1
    assert body["data"]["size"] == 10
    assert_no_sensitive_fields(resp.text)


def test_admin_can_find_seed_user_by_keyword(api_client: httpx.Client, admin_token):
    # 用 keyword 精确断言 seed 用户可达（与执行顺序/其它用例创建的数据量无关）
    resp = api_client.get("/api/v1/users", params={"keyword": "admin", "page": 1, "size": 10},
                          headers=auth_headers(admin_token))
    assert resp.status_code == 200
    usernames = [u["username"] for u in resp.json()["data"]["list"]]
    assert "admin" in usernames


def test_member_cannot_list_users(api_client: httpx.Client, member_token):
    resp = api_client.get("/api/v1/users", headers=auth_headers(member_token))
    assert resp.status_code == 403
    assert resp.json()["code"] == 403


def test_list_users_without_token_returns_401(api_client: httpx.Client):
    resp = api_client.get("/api/v1/users")
    assert resp.status_code == 401
    assert resp.json()["code"] == 401


def test_admin_can_get_user_by_id(api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    resp = api_client.get(f"/api/v1/users/{user['id']}", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["id"] == user["id"]
    assert data["username"] == user["username"]
    assert_no_sensitive_fields(resp.text)


def test_get_missing_user_returns_404(api_client: httpx.Client, admin_token):
    resp = api_client.get("/api/v1/users/999999999", headers=auth_headers(admin_token))
    assert resp.status_code == 404
    assert resp.json()["code"] == 404


def test_admin_can_create_user(api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    # 工厂即经真实 ADMIN API 创建；此处独立验证请求/响应语义
    username = USER_PREFIX + unique_suffix
    resp = api_client.post("/api/v1/users", headers=auth_headers(admin_token), json={
        "username": username, "email": f"{username}@test.local",
        "password": PASSWORD, "nickname": "created-by-api-test"})
    assert resp.status_code == 201
    body = resp.json()
    assert body["code"] == 201
    assert body["data"]["username"] == username
    assert_no_sensitive_fields(resp.text)

    # 数据真实存在: ADMIN API 可查回
    user_id = body["data"]["id"]
    resp = api_client.get(f"/api/v1/users/{user_id}", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    assert resp.json()["data"]["username"] == username


def test_create_duplicate_username_returns_409(api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    resp = api_client.post("/api/v1/users", headers=auth_headers(admin_token), json={
        "username": user["username"], "email": "dup-alt@test.local",
        "password": PASSWORD, "nickname": "dup"})
    assert resp.status_code == 409
    assert resp.json()["code"] == 409


def test_create_duplicate_email_returns_409(api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    resp = api_client.post("/api/v1/users", headers=auth_headers(admin_token), json={
        "username": USER_PREFIX + unique_suffix + "x", "email": user["email"],
        "password": PASSWORD, "nickname": "dup"})
    assert resp.status_code == 409
    assert resp.json()["code"] == 409


def test_create_invalid_request_returns_422(api_client: httpx.Client, admin_token):
    resp = api_client.post("/api/v1/users", headers=auth_headers(admin_token), json={
        "username": "ab", "email": "not-an-email", "password": "nodigit", "nickname": "n"})
    assert resp.status_code == 422
    assert resp.json()["code"] == 422


def test_admin_can_update_user(api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    new_email = USER_PREFIX + unique_suffix + "-new@test.local"
    resp = api_client.put(f"/api/v1/users/{user['id']}", headers=auth_headers(admin_token), json={
        "email": new_email, "nickname": "updated-by-api-test"})
    assert resp.status_code == 200
    assert resp.json()["data"]["email"] == new_email
    assert resp.json()["data"]["nickname"] == "updated-by-api-test"


def test_member_cannot_update_user(api_client: httpx.Client, member_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    resp = api_client.put(f"/api/v1/users/{user['id']}", headers=auth_headers(member_token), json={
        "email": "hijack@test.local", "nickname": "hijack"})
    assert resp.status_code == 403


def test_update_missing_user_returns_404(api_client: httpx.Client, admin_token):
    resp = api_client.put("/api/v1/users/999999999", headers=auth_headers(admin_token), json={
        "email": "missing@test.local", "nickname": "n"})
    assert resp.status_code == 404


def test_admin_disable_kicks_session_and_re_enable_allows_relogin(
        api_client: httpx.Client, admin_token, unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    # 目标用户真实登录
    old_token = api_client.post("/api/v1/auth/login",
                                json={"username": user["username"], "password": PASSWORD}).json()["data"]["accessToken"]
    old_headers = auth_headers(old_token)
    assert api_client.get("/api/v1/auth/me", headers=old_headers).status_code == 200

    # 禁用 → 200
    resp = api_client.patch(f"/api/v1/users/{user['id']}/status",
                            headers=auth_headers(admin_token), json={"status": "DISABLED"})
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "DISABLED"

    # 旧 token 立即失效（禁用即踢线）
    assert api_client.get("/api/v1/auth/me", headers=old_headers).status_code == 401

    # 重新启用 → 200，但不自动建会话（旧 token 仍 401），须重新登录
    resp = api_client.patch(f"/api/v1/users/{user['id']}/status",
                            headers=auth_headers(admin_token), json={"status": "ACTIVE"})
    assert resp.status_code == 200
    assert api_client.get("/api/v1/auth/me", headers=old_headers).status_code == 401

    new_token = api_client.post("/api/v1/auth/login", json={
        "username": user["username"], "password": PASSWORD}).json()["data"]["accessToken"]
    resp = api_client.get("/api/v1/auth/me", headers=auth_headers(new_token))
    assert resp.status_code == 200
    assert resp.json()["data"]["username"] == user["username"]


def test_member_cannot_change_user_status(api_client: httpx.Client, member_token,
                                          unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    resp = api_client.patch(f"/api/v1/users/{user['id']}/status",
                            headers=auth_headers(member_token), json={"status": "DISABLED"})
    assert resp.status_code == 403


def test_client_supplied_roles_field_cannot_grant_admin(api_client: httpx.Client, admin_token, unique_suffix):
    # 不接受客户端角色字段绕过权限: roles 字段被忽略, 新用户无 ADMIN 权限
    username = USER_PREFIX + unique_suffix
    resp = api_client.post("/api/v1/users", headers=auth_headers(admin_token), json={
        "username": username, "email": f"{username}@test.local",
        "password": PASSWORD, "nickname": "esc", "roles": ["ADMIN"]})
    assert resp.status_code == 201
    user_id = resp.json()["data"]["id"]

    token = api_client.post("/api/v1/auth/login", json={
        "username": username, "password": PASSWORD}).json()["data"]["accessToken"]
    resp = api_client.get("/api/v1/users", headers=auth_headers(token))
    assert resp.status_code == 403, "客户端注入 roles 字段不得获得 ADMIN 权限"


def test_malformed_bearer_token_returns_401(api_client: httpx.Client):
    resp = api_client.get("/api/v1/users", headers={"Authorization": "Bearer garbage.token.value"})
    assert resp.status_code == 401
