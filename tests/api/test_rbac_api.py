"""RBAC API 黑盒测试（P3-05）：角色/权限/用户角色/角色权限 + 权限矩阵 + 实时生效。

前置: 真实后端（conftest api_client）。
数据隔离: 角色 API_TEST_ 前缀（编码规范要求大写）、用户 api_test_ 前缀，均经真实 API 创建，
会话结束由 conftest cleanup 清理用户/Redis，本文件 cleanup fixture 清理测试角色。
"""

import httpx

from conftest import PASSWORD, USER_PREFIX, auth_headers

ROLE_PREFIX = "API_TEST_"


def test_admin_list_roles_contains_system_roles(api_client: httpx.Client, admin_token):
    resp = api_client.get("/api/v1/roles", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    roles = resp.json()["data"]
    codes = {r["code"] for r in roles}
    assert {"ADMIN", "MEMBER"} <= codes
    assert all(r["system"] for r in roles if r["code"] in ("ADMIN", "MEMBER"))


def test_member_list_roles_forbidden_and_unauthenticated_401(api_client: httpx.Client, member_token):
    assert api_client.get("/api/v1/roles", headers=auth_headers(member_token)).status_code == 403
    assert api_client.get("/api/v1/roles").status_code == 401


def test_role_crud_success_and_conflict(api_client: httpx.Client, admin_token, unique_suffix, cleanup_roles):
    code = ROLE_PREFIX + unique_suffix.upper()
    resp = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                           json={"code": code, "name": "自动化角色", "description": "p3-05"})
    assert resp.status_code == 201
    role_id = resp.json()["data"]["id"]
    cleanup_roles.append(role_id)

    # 重复编码 409
    resp = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                           json={"code": code, "name": "重复", "description": None})
    assert resp.status_code == 409

    # 非法编码 422
    resp = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                           json={"code": "bad-code", "name": "非法", "description": None})
    assert resp.status_code == 422

    # 更新（code 不可改）
    resp = api_client.put(f"/api/v1/roles/{role_id}", headers=auth_headers(admin_token),
                          json={"name": "改名角色", "description": "new"})
    assert resp.status_code == 200
    assert resp.json()["data"]["name"] == "改名角色"
    assert resp.json()["data"]["code"] == code


def test_system_role_delete_protected(api_client: httpx.Client, admin_token):
    roles = api_client.get("/api/v1/roles", headers=auth_headers(admin_token)).json()["data"]
    admin_id = next(r["id"] for r in roles if r["code"] == "ADMIN")
    resp = api_client.delete(f"/api/v1/roles/{admin_id}", headers=auth_headers(admin_token))
    assert resp.status_code == 400


def test_role_permission_replace_and_unknown_404(api_client: httpx.Client, admin_token, unique_suffix, cleanup_roles):
    code = ROLE_PREFIX + unique_suffix.upper() + "P"
    role_id = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                              json={"code": code, "name": "权限绑定", "description": None}).json()["data"]["id"]
    cleanup_roles.append(role_id)

    resp = api_client.put(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token),
                          json={"permissionCodes": ["user:list", "role:list"]})
    assert resp.status_code == 200
    assert sorted(resp.json()["data"]) == ["role:list", "user:list"]

    resp = api_client.put(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token),
                          json={"permissionCodes": ["no_such:perm"]})
    assert resp.status_code == 404

    resp = api_client.get(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token))
    assert resp.status_code == 200


def test_permissions_list_contains_system_permissions(api_client: httpx.Client, admin_token):
    resp = api_client.get("/api/v1/permissions", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    codes = {p["code"] for p in resp.json()["data"]}
    assert {"user:create", "role:create", "permission:list"} <= codes
    assert "passwordHash" not in resp.text


def test_user_roles_get_assign_revoke_replace(api_client: httpx.Client, admin_token,
                                              unique_suffix, create_api_user):
    user = create_api_user(unique_suffix)
    headers = auth_headers(admin_token)

    resp = api_client.post(f"/api/v1/users/{user['id']}/roles", headers=headers,
                           json={"roleCode": "MEMBER"})
    assert resp.status_code == 200
    assert resp.json()["data"] == ["MEMBER"]

    # 幂等重复分配
    resp = api_client.post(f"/api/v1/users/{user['id']}/roles", headers=headers,
                           json={"roleCode": "MEMBER"})
    assert resp.json()["data"] == ["MEMBER"]

    # replace
    resp = api_client.put(f"/api/v1/users/{user['id']}/roles", headers=headers,
                          json={"roleCodes": ["MEMBER"]})
    assert resp.status_code == 200

    # 未知角色 404
    resp = api_client.post(f"/api/v1/users/{user['id']}/roles", headers=headers,
                           json={"roleCode": "NO_SUCH_ROLE_X"})
    assert resp.status_code == 404

    # 回收
    resp = api_client.delete(f"/api/v1/users/{user['id']}/roles/MEMBER", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["data"] == []


def test_member_cannot_manage_rbac(api_client: httpx.Client, member_token):
    assert api_client.get("/api/v1/permissions", headers=auth_headers(member_token)).status_code == 403
    assert api_client.post("/api/v1/roles", headers=auth_headers(member_token),
                           json={"code": "X_TEST", "name": "x", "description": None}).status_code == 403


def test_permission_grant_revoke_takes_effect_immediately(api_client: httpx.Client, admin_token,
                                                          unique_suffix, create_api_user, cleanup_roles):
    """接线核心: 授权/收权对实时解析即时生效（重登录建立的新会话验证权限变化）。"""
    user = create_api_user(unique_suffix)
    first_token = _login(api_client, user)
    # 工厂用户仅 MEMBER，无管理权限
    assert api_client.get("/api/v1/roles", headers=auth_headers(first_token)).status_code == 403

    # 创建绑定 role:list 的角色并授予用户
    code = ROLE_PREFIX + unique_suffix.upper() + "L"
    role_id = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                              json={"code": code, "name": "实时权限", "description": None}).json()["data"]["id"]
    cleanup_roles.append(role_id)
    api_client.put(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token),
                   json={"permissionCodes": ["role:list"]})
    api_client.post(f"/api/v1/users/{user['id']}/roles", headers=auth_headers(admin_token),
                    json={"roleCode": code})

    # 授权后新会话可访问
    granted_token = _login(api_client, user)
    assert api_client.get("/api/v1/roles", headers=auth_headers(granted_token)).status_code == 200

    # /me/permissions 反映新权限
    perms = api_client.get("/api/v1/auth/me/permissions", headers=auth_headers(granted_token)).json()["data"]
    assert "role:list" in perms

    # 回收后（重登录）立即 403
    api_client.delete(f"/api/v1/users/{user['id']}/roles/{code}", headers=auth_headers(admin_token))
    revoked_token = _login(api_client, user)
    assert api_client.get("/api/v1/roles", headers=auth_headers(revoked_token)).status_code == 403


def _login(api_client: httpx.Client, user: dict) -> str:
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": user["password"]})
    assert resp.status_code == 200
    return resp.json()["data"]["accessToken"]
