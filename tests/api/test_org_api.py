"""组织架构 API 黑盒测试（Phase 4 Gate）：真实 HTTP。

覆盖: 组织 CRUD/唯一性/权限矩阵/OWNER 数据级规则/成员管理/部门树防环。
数据隔离: 组织 ORG_SMOKE_ 前缀、用户 api_test_ 前缀（conftest 清理），组织经 API 由 owner 删除。
"""

import httpx

from conftest import PASSWORD, USER_PREFIX, auth_headers

ORG_PREFIX = "ORG_SMOKE_"


def test_rbac_permission_matrix_for_orgs(api_client: httpx.Client, admin_token, member_token):
    assert api_client.get("/api/v1/orgs", headers=auth_headers(admin_token)).status_code == 200
    assert api_client.get("/api/v1/orgs", headers=auth_headers(member_token)).status_code == 403
    assert api_client.get("/api/v1/orgs").status_code == 401


def test_org_crud_and_owner_rule(api_client: httpx.Client, admin_token, member_token, unique_suffix, cleanup_orgs):
    # admin 创建 → 201，且为 OWNER
    resp = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                           json={"name": "Gate 组织", "code": ORG_PREFIX + unique_suffix.upper(),
                                 "description": "gate"})
    assert resp.status_code == 201
    org = resp.json()["data"]
    cleanup_orgs.append(org["id"])

    # 重复编码 409
    resp = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                           json={"name": "dup", "code": ORG_PREFIX + unique_suffix.upper(), "description": None})
    assert resp.status_code == 409

    # 非法编码 422
    resp = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                           json={"name": "bad", "code": "bad-code", "description": None})
    assert resp.status_code == 422

    # 更新 200
    resp = api_client.put(f"/api/v1/orgs/{org['id']}", headers=auth_headers(admin_token),
                          json={"name": "Gate 组织-改"})
    assert resp.status_code == 200
    assert resp.json()["data"]["name"] == "Gate 组织-改"

    # OWNER 数据级规则: member（无 authority）403；admin（有 authority 但非 OWNER）也 403；
    # 先把 org:delete 授予 member 使"有 authority 非 OWNER"场景可测——简化：直接验证 owner 删除成功
    resp = api_client.delete(f"/api/v1/orgs/{org['id']}", headers=auth_headers(member_token))
    assert resp.status_code == 403  # 无 authority

    resp = api_client.delete(f"/api/v1/orgs/{org['id']}", headers=auth_headers(admin_token))
    assert resp.status_code == 200  # owner

    # 删除后 404
    assert api_client.get(f"/api/v1/orgs/{org['id']}", headers=auth_headers(admin_token)).status_code == 404


def test_non_owner_with_authority_cannot_delete_org(
        api_client: httpx.Client, admin_token, member_token, unique_suffix, create_api_user, cleanup_roles,
        cleanup_orgs):
    """有 org:delete authority 但非 OWNER → 403（数据级规则）。"""
    member_user = create_api_user(unique_suffix)
    member_token2 = api_client.post("/api/v1/auth/login",
                                    json={"username": member_user["username"], "password": PASSWORD}
                                    ).json()["data"]["accessToken"]

    # 经 RBAC API: 建角色绑 org:create/org:delete 授予该用户
    code = "GATE_CREATOR_" + unique_suffix.upper()
    role_id = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                              json={"code": code, "name": "gate-creator", "description": None}
                              ).json()["data"]["id"]
    cleanup_roles.append(role_id)
    api_client.put(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token),
                   json={"permissionCodes": ["org:create", "org:delete"]})
    api_client.post(f"/api/v1/users/{member_user['id']}/roles", headers=auth_headers(admin_token),
                    json={"roleCode": code})

    # 该用户创建组织（成为 OWNER）
    resp = api_client.post("/api/v1/orgs", headers=auth_headers(member_token2),
                           json={"name": "他组织", "code": ORG_PREFIX + unique_suffix.upper() + "O",
                                 "description": None})
    assert resp.status_code == 201
    org_id = resp.json()["data"]["id"]

    # admin: 有 org:delete authority 但非 OWNER → 403
    resp = api_client.delete(f"/api/v1/orgs/{org_id}", headers=auth_headers(admin_token))
    assert resp.status_code == 403
    assert resp.json()["code"] == 403

    # OWNER 自删（自清理，不经 cleanup_orgs——admin 删非 owner 组织会 403）
    resp = api_client.delete(f"/api/v1/orgs/{org_id}", headers=auth_headers(member_token2))
    assert resp.status_code == 200


def test_department_tree_rules(api_client: httpx.Client, admin_token, unique_suffix, cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "部门组织", "code": ORG_PREFIX + unique_suffix.upper() + "D",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])

    parent = api_client.post(f"/api/v1/orgs/{org['id']}/departments", headers=auth_headers(admin_token),
                             json={"name": "研发部", "code": "RD", "description": None, "parentId": None}
                             ).json()["data"]
    child = api_client.post(f"/api/v1/orgs/{org['id']}/departments", headers=auth_headers(admin_token),
                            json={"name": "后端组", "code": "BE", "description": None, "parentId": parent["id"]}
                            ).json()["data"]

    # 同组织 code 重复 → 409
    resp = api_client.post(f"/api/v1/orgs/{org['id']}/departments", headers=auth_headers(admin_token),
                           json={"name": "重名", "code": "RD", "description": None, "parentId": None})
    assert resp.status_code == 409

    # 防环: 父挂子 → 400；自引用 → 400
    resp = api_client.patch(f"/api/v1/departments/{parent['id']}", headers=auth_headers(admin_token),
                            json={"name": "研发部", "parentId": child["id"]})
    assert resp.status_code == 400
    resp = api_client.patch(f"/api/v1/departments/{parent['id']}", headers=auth_headers(admin_token),
                            json={"name": "研发部", "parentId": parent["id"]})
    assert resp.status_code == 400

    # 删除父 → 子提升为根
    resp = api_client.delete(f"/api/v1/departments/{parent['id']}", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    resp = api_client.get(f"/api/v1/departments/{child['id']}", headers=auth_headers(admin_token))
    # parentId=null 被 NON_NULL 序列化省略——提升为根后无 parentId 字段
    assert resp.json()["data"].get("parentId") is None


def test_org_member_management(api_client: httpx.Client, admin_token, unique_suffix, create_api_user,
                               cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "成员组织", "code": ORG_PREFIX + unique_suffix.upper() + "M",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    member = create_api_user(unique_suffix)

    resp = api_client.get(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    assert len(resp.json()["data"]) == 1
    assert resp.json()["data"][0]["role"] == "OWNER"

    resp = api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": member["id"], "role": "MEMBER", "departmentId": None})
    assert resp.status_code == 200

    # 重复添加 409
    resp = api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": member["id"], "role": "MEMBER", "departmentId": None})
    assert resp.status_code == 409

    # 未知用户 404
    resp = api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": 999999999, "role": "MEMBER", "departmentId": None})
    assert resp.status_code == 404

    # 移除 OWNER 400
    resp = api_client.delete(f"/api/v1/orgs/{org['id']}/members/{org['ownerId']}",
                             headers=auth_headers(admin_token))
    assert resp.status_code == 400
