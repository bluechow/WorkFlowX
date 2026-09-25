"""Test Case API 黑盒测试（Phase 20 / V1.1; P20-07）：真实 HTTP。

规则（ADR-022）: testcase:* authority + 项目成员数据级（读写均要求，成员校验先于目标查找）；
目录树防环；目录删除级联子目录、用例退回未分类；编号项目内唯一递增。
"""

import uuid

import httpx
import pytest

from conftest import auth_headers

KEY_PREFIX = "P20TC"
ORG_PREFIX = "P20TC_ORG_"


@pytest.fixture()
def tc_env(api_client, admin_token, unique_suffix, cleanup_orgs):
    """组织→项目→目录树（登录/异常流两个子目录）+ user1 入组织与项目。"""
    h = auth_headers(admin_token)
    su = unique_suffix.upper()
    org = api_client.post("/api/v1/orgs", headers=h,
                          json={"name": f"P20TC 组织 {su}", "code": f"P20TCORG{su}",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=h,
                    json={"userId": 2, "role": "MEMBER", "departmentId": None})
    project = api_client.post("/api/v1/projects", headers=h,
                              json={"name": "P20TC 项目", "key": f"P20TC{su}",
                                    "orgId": org["id"], "description": None}).json()["data"]
    api_client.post(f"/api/v1/projects/{project['id']}/members", headers=h,
                    json={"userId": 2, "role": "MEMBER"})
    root = api_client.post(f"/api/v1/projects/{project['id']}/testcase-directories", headers=h,
                           json={"name": "登录模块", "parentId": None}).json()["data"]
    child = api_client.post(f"/api/v1/projects/{project['id']}/testcase-directories", headers=h,
                            json={"name": "异常流", "parentId": root["id"]}).json()["data"]
    return {"h": h, "org": org, "project": project, "root": root, "child": child,
            "member_h": auth_headers(admin_token)}


def _base(env):
    return f"/api/v1/projects/{env['project']['id']}/testcases"


def _dirbase(env):
    return f"/api/v1/projects/{env['project']['id']}/testcase-directories"


def test_directory_tree_create_and_list(api_client, tc_env):
    resp = api_client.get(_dirbase(tc_env), headers=tc_env["h"])
    assert resp.status_code == 200
    dirs = {d["name"]: d for d in resp.json()["data"]}
    assert "登录模块" in dirs and "异常流" in dirs
    assert dirs["异常流"]["parentId"] == dirs["登录模块"]["id"]


def test_directory_move_cycle_rejected(api_client, tc_env):
    root, child = tc_env["root"], tc_env["child"]
    # 移动根到自身子目录 → 400 成环
    resp = api_client.put(f"{_dirbase(tc_env)}/{root['id']}", headers=tc_env["h"],
                          json={"name": None, "parentId": child["id"]})
    assert resp.status_code == 400, resp.text
    # 重命名 → 200
    resp = api_client.put(f"{_dirbase(tc_env)}/{root['id']}", headers=tc_env["h"],
                          json={"name": "登录模块-改", "parentId": None})
    assert resp.status_code == 200
    assert resp.json()["data"]["name"] == "登录模块-改"


def test_directory_delete_cascades_subdirs_cases(api_client, tc_env):
    case = api_client.post(_base(tc_env), headers=tc_env["h"],
                           json={"title": "P20 目录内用例", "caseType": "FUNCTIONAL",
                                 "priority": "HIGH", "directoryId": tc_env["root"]["id"]}).json()["data"]
    resp = api_client.delete(f"{_dirbase(tc_env)}/{tc_env['root']['id']}", headers=tc_env["h"])
    assert resp.status_code == 200
    # 子目录级联删除（list 中不再出现）
    dirs = api_client.get(_dirbase(tc_env), headers=tc_env["h"]).json()["data"]
    ids = {d["id"] for d in dirs}
    assert tc_env["root"]["id"] not in ids and tc_env["child"]["id"] not in ids
    # 用例退回未分类（directoryId=null），数据不丢
    resp = api_client.get(f"{_base(tc_env)}/{case['id']}", headers=tc_env["h"])
    assert resp.status_code == 200
    # NON_NULL 序列化省略 null 字段（记忆坑 ⑯）——用 .get() 断言
    assert resp.json()["data"].get("directoryId") is None


def test_case_create_sequential_no_and_filters(api_client, tc_env):
    base = _base(tc_env)
    c1 = api_client.post(base, headers=tc_env["h"],
                         json={"title": "P20 登录成功", "preconditions": "已有账号",
                               "steps": "1.输入 2.提交", "expected": "跳转首页",
                               "caseType": "FUNCTIONAL", "priority": "HIGH",
                               "status": "ACTIVE", "directoryId": tc_env["root"]["id"]}).json()["data"]
    assert c1["testcaseNo"] == 1
    c2 = api_client.post(base, headers=tc_env["h"],
                         json={"title": "P20 支付回归", "caseType": "REGRESSION",
                               "priority": "CRITICAL", "status": "DRAFT",
                               "directoryId": None}).json()["data"]
    assert c2["testcaseNo"] == 2

    # keyword 过滤
    resp = api_client.get(base, params={"keyword": "回归"}, headers=tc_env["h"]).json()["data"]
    assert resp["total"] == 1 and resp["list"][0]["id"] == c2["id"]
    # directory 过滤
    resp = api_client.get(base, params={"directoryId": tc_env["root"]["id"]}, headers=tc_env["h"]).json()["data"]
    assert resp["total"] == 1 and resp["list"][0]["id"] == c1["id"]
    # 未分类（0 = IS NULL）
    resp = api_client.get(base, params={"directoryId": 0}, headers=tc_env["h"]).json()["data"]
    assert resp["total"] == 1 and resp["list"][0]["id"] == c2["id"]
    # type+status 组合
    resp = api_client.get(base, params={"caseType": "REGRESSION", "status": "DRAFT"},
                          headers=tc_env["h"]).json()["data"]
    assert resp["total"] == 1


def test_case_validation_errors(api_client, tc_env):
    base = _base(tc_env)
    assert api_client.post(base, headers=tc_env["h"],
                           json={"title": "", "caseType": "FUNCTIONAL"}).status_code == 422
    assert api_client.post(base, headers=tc_env["h"],
                           json={"title": "P20 坏枚举", "caseType": "NOT_A_TYPE"}).status_code == 422
    assert api_client.post(base, headers=tc_env["h"],
                           json={"title": "P20 坏目录", "caseType": "FUNCTIONAL",
                                 "directoryId": 999999999}).status_code == 400
    # 创建即 DEPRECATED → 400
    assert api_client.post(base, headers=tc_env["h"],
                           json={"title": "P20 废弃", "caseType": "FUNCTIONAL",
                                 "status": "DEPRECATED"}).status_code == 400


def test_case_update_and_delete(api_client, tc_env):
    created = api_client.post(_base(tc_env), headers=tc_env["h"],
                              json={"title": "P20 待编辑", "caseType": "SMOKE",
                                    "priority": "LOW", "directoryId": None}).json()["data"]
    resp = api_client.put(f"{_base(tc_env)}/{created['id']}", headers=tc_env["h"],
                          json={"title": "P20 已编辑", "status": "DEPRECATED",
                                "directoryId": tc_env["child"]["id"]})
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["title"] == "P20 已编辑" and data["status"] == "DEPRECATED"
    assert data["directoryId"] == tc_env["child"]["id"]
    assert api_client.delete(f"{_base(tc_env)}/{created['id']}", headers=tc_env["h"]).status_code == 200
    assert api_client.get(f"{_base(tc_env)}/{created['id']}",
                          headers=tc_env["h"]).status_code == 404


def test_member_can_read_write_non_member_403(
        api_client, admin_token, tc_env, unique_suffix, create_api_user):
    """数据级: 动态成员获授 testcase 权限且为项目成员 → 可读写；非成员项目 403 先于目标查找。
    动态用户（P17 教训：不重登 seed user1——会顶掉 session 级 member_token 污染后续用例）。"""
    # 动态用户加入组织与项目（tc_env setup 已加 user1=2；此处建独立动态用户）
    member = create_api_user()
    api_client.post(f"/api/v1/orgs/{tc_env['org']['id']}/members", headers=tc_env["h"],
                    json={"userId": member["id"], "role": "MEMBER", "departmentId": None})
    api_client.post(f"/api/v1/projects/{tc_env['project']['id']}/members", headers=tc_env["h"],
                    json={"userId": member["id"], "role": "MEMBER"})
    # 真实 RBAC 路径: 建角色（testcase:list/get/create）绑动态用户
    su = unique_suffix.upper()
    role = api_client.post("/api/v1/roles", headers=tc_env["h"],
                           json={"code": f"P20TCM{su}", "name": f"p20tc-m-{su}",
                                 "description": None}).json()["data"]
    api_client.put(f"/api/v1/roles/{role['id']}/permissions", headers=tc_env["h"],
                   json={"permissionCodes": ["testcase:list", "testcase:get", "testcase:create"]})
    api_client.post(f"/api/v1/users/{member['id']}/roles", headers=tc_env["h"],
                    json={"roleCode": f"P20TCM{su}"})
    member_h = auth_headers(api_client.post("/api/v1/auth/login",
                                            json={"username": member["username"],
                                                  "password": member["password"]}
                                            ).json()["data"]["accessToken"])
    created = api_client.post(_base(tc_env), headers=tc_env["h"],
                              json={"title": "P20 成员用例", "caseType": "FUNCTIONAL",
                                    "priority": "MEDIUM", "directoryId": None}).json()["data"]
    # 项目成员且有 authority → 读成功
    resp = api_client.get(f"{_base(tc_env)}/{created['id']}", headers=member_h)
    assert resp.status_code == 200
    # 未加入的新项目 → authority 通过但数据级 403 先于目标查找
    su2 = unique_suffix.upper() + "B"
    org2 = api_client.post("/api/v1/orgs", headers=tc_env["h"],
                           json={"name": f"P20TC 组织B {su2}", "code": f"P20TCORGB{su2}",
                                 "description": None}).json()["data"]
    proj2 = api_client.post("/api/v1/projects", headers=tc_env["h"],
                            json={"name": "P20TC 项目B", "key": f"P20TCBB{su2}",
                                  "orgId": org2["id"], "description": None}).json()["data"]
    resp = api_client.get(f"/api/v1/projects/{proj2['id']}/testcases", headers=member_h)
    assert resp.status_code == 403, "非项目成员 → 数据级 403（先于目标查找）"
    # 回收: 解绑角色、删角色（动态用户进 api_test_ 清理通道）
    api_client.delete(f"/api/v1/users/{member['id']}/roles/P20TCM{su}", headers=tc_env["h"])
    api_client.delete(f"/api/v1/roles/{role['id']}", headers=tc_env["h"])


def test_permission_matrix_401_403(api_client, admin_token, create_api_user, tc_env):
    """未认证 401；无 testcase:* authority 的普通用户 403——权限边界在 API 层。
    用动态用户（P17 教训：重登 seed user1 会顶掉 session 级 member_token）。"""
    user = create_api_user()
    h = auth_headers(api_client.post("/api/v1/auth/login",
                                     json={"username": user["username"],
                                           "password": user["password"]}).json()["data"]["accessToken"])
    assert api_client.get(_base(tc_env)).status_code == 401
    resp = api_client.get(_base(tc_env), headers=h)
    assert resp.status_code == 403
    resp = api_client.post(_dirbase(tc_env), headers=h,
                           json={"name": "P20 越权目录", "parentId": None})
    assert resp.status_code == 403
