import httpx

from conftest import PASSWORD, USER_PREFIX, auth_headers

ORG_PREFIX = "PYP5_ORG_"
KEY_PREFIX = "PYP5"


def test_project_permission_matrix(api_client: httpx.Client, admin_token, member_token):
    assert api_client.get("/api/v1/projects", headers=auth_headers(admin_token)).status_code == 200
    assert api_client.get("/api/v1/projects", headers=auth_headers(member_token)).status_code == 403
    assert api_client.get("/api/v1/projects").status_code == 401


def test_project_crud_lifecycle(api_client: httpx.Client, admin_token,
                                unique_suffix, cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "Py 组织", "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    headers = auth_headers(admin_token)

    # create 201
    resp = api_client.post("/api/v1/projects", headers=headers,
                           json={"name": "Py 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                 "orgId": org["id"], "description": "py"})
    assert resp.status_code == 201
    project = resp.json()["data"]
    assert project["status"] == "ACTIVE"
    assert project["ownerId"] == org["ownerId"]

    # duplicate key 409
    resp = api_client.post("/api/v1/projects", headers=headers,
                           json={"name": "dup", "key": KEY_PREFIX + unique_suffix.upper(),
                                 "orgId": org["id"], "description": None})
    assert resp.status_code == 409

    # invalid key 422
    resp = api_client.post("/api/v1/projects", headers=headers,
                           json={"name": "bad", "key": "bad-key", "orgId": org["id"], "description": None})
    assert resp.status_code == 422

    # missing org 404
    resp = api_client.post("/api/v1/projects", headers=headers,
                           json={"name": "noorg", "key": KEY_PREFIX + unique_suffix.upper() + "X",
                                 "orgId": 999999999, "description": None})
    assert resp.status_code == 404

    # get 200
    resp = api_client.get(f"/api/v1/projects/{project['id']}", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["data"]["key"] == project["key"]

    # get missing 404
    assert api_client.get("/api/v1/projects/999999999", headers=headers).status_code == 404

    # update: key/orgId 不可改（DTO 无此字段）——只改 name
    resp = api_client.put(f"/api/v1/projects/{project['id']}", headers=headers,
                          json={"name": "Py 项目-改"})
    assert resp.status_code == 200
    assert resp.json()["data"]["name"] == "Py 项目-改"
    assert resp.json()["data"]["key"] == project["key"]

    # archive → restore
    resp = api_client.patch(f"/api/v1/projects/{project['id']}/status", headers=headers,
                            json={"status": "ARCHIVED"})
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "ARCHIVED"
    resp = api_client.patch(f"/api/v1/projects/{project['id']}/status", headers=headers,
                            json={"status": "ACTIVE"})
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "ACTIVE"


def test_project_list_filters(api_client: httpx.Client, admin_token, unique_suffix, cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "Py 过滤组织", "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    headers = auth_headers(admin_token)
    for key, name, status in [(KEY_PREFIX + unique_suffix.upper() + "A", "过滤甲", "ACTIVE"),
                              (KEY_PREFIX + unique_suffix.upper() + "B", "过滤乙", "ACTIVE")]:
        api_client.post("/api/v1/projects", headers=headers,
                        json={"name": name, "key": key, "orgId": org["id"], "description": None})
        if status == "ARCHIVED":
            pass
    pid_b = [p for p in api_client.get("/api/v1/projects", params={"keyword": KEY_PREFIX + unique_suffix.upper()},
                                       headers=auth_headers(admin_token)).json()["data"]["list"]
             if p["name"] == "过滤乙"][0]["id"]
    api_client.patch(f"/api/v1/projects/{pid_b}/status", headers=headers, json={"status": "ARCHIVED"})

    # keyword 命中两个
    resp = api_client.get("/api/v1/projects", params={"keyword": KEY_PREFIX + unique_suffix.upper()},
                          headers=auth_headers(admin_token))
    assert resp.json()["data"]["total"] == 2
    # status=ACTIVE 只剩 1
    resp = api_client.get("/api/v1/projects",
                          params={"keyword": KEY_PREFIX + unique_suffix.upper(), "status": "ACTIVE"},
                          headers=auth_headers(admin_token))
    assert resp.json()["data"]["total"] == 1
    # orgId 过滤
    resp = api_client.get("/api/v1/projects", params={"orgId": org["id"]},
                          headers=auth_headers(admin_token))
    assert resp.json()["data"]["total"] == 2


def test_data_level_membership_required_for_writes(
        api_client: httpx.Client, admin_token, unique_suffix, create_api_user, cleanup_orgs, cleanup_roles):
    """authority 有（admin）但非组织成员 → 写 403；加入组织后 → 200。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "归属组织", "code": ORG_PREFIX + unique_suffix.upper() + "B",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    # 建一个"非 admin 的管理者"用户，授予 project:* authority
    manager = create_api_user(unique_suffix)
    code = "GATE_PM_" + unique_suffix.upper()
    role_id = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                              json={"code": code, "name": "pm", "description": None}).json()["data"]["id"]
    cleanup_roles.append(role_id)
    api_client.put(f"/api/v1/roles/{role_id}/permissions", headers=auth_headers(admin_token),
                   json={"permissionCodes": ["project:create", "project:update", "project:list"]})
    api_client.post(f"/api/v1/users/{manager['id']}/roles", headers=auth_headers(admin_token),
                    json={"roleCode": code})

    manager_token = api_client.post("/api/v1/auth/login",
                                    json={"username": manager["username"], "password": PASSWORD}
                                    ).json()["data"]["accessToken"]
    mh = auth_headers(manager_token)

    # authority 有 + 非组织成员 → 403
    resp = api_client.post("/api/v1/projects", headers=mh,
                           json={"name": "越权", "key": KEY_PREFIX + unique_suffix.upper() + "M",
                                 "orgId": org["id"], "description": None})
    assert resp.status_code == 403

    # 加入组织成员后 → 201
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": manager["id"], "role": "MEMBER", "departmentId": None})
    resp = api_client.post("/api/v1/projects", headers=mh,
                           json={"name": "成员创建", "key": KEY_PREFIX + unique_suffix.upper() + "M",
                                 "orgId": org["id"], "description": None})
    assert resp.status_code == 201
    pid = resp.json()["data"]["id"]

    # 移出组织后 → 写操作 403（更新）
    api_client.delete(f"/api/v1/orgs/{org['id']}/members/{manager['id']}",
                      headers=auth_headers(admin_token))
    resp = api_client.put(f"/api/v1/projects/{pid}", headers=mh, json={"name": "移出后改"})
    assert resp.status_code == 403


def test_project_member_lifecycle(api_client: httpx.Client, admin_token, unique_suffix,
                                  create_api_user, cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "成员组织", "code": ORG_PREFIX + unique_suffix.upper() + "M2",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "成员项目", "key": KEY_PREFIX + unique_suffix.upper() + "MEM",
                                    "orgId": org["id"], "description": None}).json()["data"]
    new_member = create_api_user(unique_suffix + "m")
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": new_member["id"], "role": "MEMBER", "departmentId": None})

    # OWNER 自动成员
    resp = api_client.get(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    members = resp.json()["data"]
    assert len(members) == 1 and members[0]["role"] == "OWNER"

    # 添加：非组织成员 400（先建一个不在组织内的用户）
    outsider = create_api_user(unique_suffix + "out")
    resp = api_client.post(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": outsider["id"], "role": "MEMBER"})
    assert resp.status_code == 400

    # 添加：组织成员 200
    resp = api_client.post(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": new_member["id"], "role": "MANAGER"})
    assert resp.status_code == 200
    assert resp.json()["data"]["role"] == "MANAGER"

    # 重复 409
    resp = api_client.post(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token),
                           json={"userId": new_member["id"], "role": "MEMBER"})
    assert resp.status_code == 409

    # OWNER 移除 400
    resp = api_client.delete(f"/api/v1/projects/{project['id']}/members/{org['ownerId']}",
                             headers=auth_headers(admin_token))
    assert resp.status_code == 400

    # 移除 MANAGER 200，再移除 404
    resp = api_client.delete(f"/api/v1/projects/{project['id']}/members/{new_member['id']}",
                             headers=auth_headers(admin_token))
    assert resp.status_code == 200
    resp = api_client.delete(f"/api/v1/projects/{project['id']}/members/{new_member['id']}",
                             headers=auth_headers(admin_token))
    assert resp.status_code == 404
