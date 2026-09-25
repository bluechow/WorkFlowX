"""测试计划 API 黑盒测试（Phase 21 / V1.1; P21）：真实 HTTP。

规则（ADR-023）: testplan:* authority + 项目成员数据级（读写均要求，成员校验先于目标查找）；
COMPLETED 计划拒绝执行/添加；FAIL/BLOCKED 才可关联 Bug；统计实时计算。
"""

import uuid

import pytest

from conftest import auth_headers

KEY_PREFIX = "P21TP"
ORG_PREFIX = "P21TP_ORG_"


@pytest.fixture()
def tp_env(api_client, admin_token, unique_suffix, cleanup_orgs):
    h = auth_headers(admin_token)
    su = unique_suffix.upper()
    org = api_client.post("/api/v1/orgs", headers=h,
                          json={"name": f"P21TP 组织 {su}", "code": f"P21TPORG{su}",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=h,
                    json={"userId": 2, "role": "MEMBER", "departmentId": None})
    project = api_client.post("/api/v1/projects", headers=h,
                              json={"name": "P21TP 项目", "key": f"P21TP{su}",
                                    "orgId": org["id"], "description": None}).json()["data"]
    api_client.post(f"/api/v1/projects/{project['id']}/members", headers=h,
                    json={"userId": 2, "role": "MEMBER"})
    # 用例库 3 条（一条进目录、两条未分类）
    dir_resp = api_client.post(f"/api/v1/projects/{project['id']}/testcase-directories",
                               headers=h, json={"name": "P21TP 目录", "parentId": None})
    directory_id = dir_resp.json()["data"]["id"] if dir_resp.status_code == 201 else None
    cases = []
    for i, (title, d) in enumerate([("P21 用例1", directory_id), ("P21 用例2", None), ("P21 用例3", None)]):
        resp = api_client.post(f"/api/v1/projects/{project['id']}/testcases", headers=h,
                               json={"title": title, "caseType": "FUNCTIONAL",
                                     "priority": "MEDIUM", "status": "ACTIVE",
                                     "directoryId": d})
        assert resp.status_code == 201, resp.text
        cases.append(resp.json()["data"]["id"])
    return {"h": h, "org": org, "project": project, "directory_id": directory_id, "cases": cases}


def _base(env):
    return f"/api/v1/projects/{env['project']['id']}/testplans"


def _create_plan(api_client, env, name="P21 计划"):
    resp = api_client.post(_base(env), headers=env["h"], json={"name": name})
    assert resp.status_code == 201, resp.text
    return resp.json()["data"]


def _add_all(api_client, env, plan_id):
    resp = api_client.post(f"{_base(env)}/{plan_id}/items", headers=env["h"],
                           json={"caseIds": env["cases"]})
    assert resp.status_code == 200, resp.text
    return resp.json()["data"]


def test_plan_crud_and_status_flow(api_client, tp_env):
    plan = _create_plan(api_client, tp_env)
    assert plan["status"] == "NOT_STARTED"
    # 名称+状态更新
    resp = api_client.put(f"{_base(tc_env := tp_env)}/{plan['id']}", headers=tp_env["h"],
                          json={"name": "P21 计划-改", "status": "RUNNING"})
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "RUNNING"
    # 删除
    assert api_client.delete(f"{_base(tp_env)}/{plan['id']}", headers=tp_env["h"]).status_code == 200
    assert api_client.get(f"{_base(tp_env)}/{plan['id']}", headers=tp_env["h"]).status_code == 404


def test_plan_items_add_skip_and_remove(api_client, tp_env):
    plan = _create_plan(api_client, tp_env)
    added = _add_all(api_client, tp_env, plan["id"])
    assert added == 3
    # 重复添加跳过
    assert _add_all(api_client, tp_env, plan["id"]) == 0
    # 条目列表（含用例摘要）
    items = api_client.get(f"{_base(tp_env)}/{plan['id']}/items", headers=tp_env["h"]).json()["data"]
    assert len(items) == 3
    assert items[0]["testcaseNo"] in (1, 2, 3)
    assert items[0]["caseTitle"].startswith("P21 用例")
    # 移除一个
    assert api_client.delete(f"{_base(tp_env)}/{plan['id']}/items/{items[0]['id']}",
                             headers=tp_env["h"]).status_code == 200
    items = api_client.get(f"{_base(tp_env)}/{plan['id']}/items", headers=tp_env["h"]).json()["data"]
    assert len(items) == 2


def test_execute_flow_and_stats(api_client, tp_env):
    plan = _create_plan(api_client, tp_env)
    _add_all(api_client, tp_env, plan["id"])
    items = api_client.get(f"{_base(tp_env)}/{plan['id']}/items", headers=tp_env["h"]).json()["data"]
    # 执行: PASS → FAIL(关联Bug) → BLOCKED
    r1 = api_client.put(f"{_base(tp_env)}/{plan['id']}/items/{items[0]['id']}/execute",
                        headers=tp_env["h"], json={"result": "PASS"})
    assert r1.status_code == 200 and r1.json()["data"]["result"] == "PASS"
    # 首次执行自动 NOT_STARTED→RUNNING
    assert api_client.get(f"{_base(tp_env)}/{plan['id']}", headers=tp_env["h"]).json()["data"]["status"] == "RUNNING"
    # 建一个 Bug 用于关联
    issue = api_client.post(f"/api/v1/projects/{tp_env['project']['id']}/issues", headers=tp_env["h"],
                            json={"title": "P21 执行失败 Bug", "caseType": None,
                                  "type": "BUG", "priority": "HIGH", "assigneeId": None}).json()["data"]
    r2 = api_client.put(f"{_base(tp_env)}/{plan['id']}/items/{items[1]['id']}/execute",
                        headers=tp_env["h"], json={"result": "FAIL", "note": "登录失败", "issueId": issue["id"]})
    assert r2.status_code == 200 and r2.json()["data"]["issueId"] == issue["id"]
    r3 = api_client.put(f"{_base(tp_env)}/{plan['id']}/items/{items[2]['id']}/execute",
                        headers=tp_env["h"], json={"result": "BLOCKED", "note": "被阻塞"})
    assert r3.status_code == 200
    # 统计实时计算
    detail = api_client.get(f"{_base(tp_env)}/{plan['id']}", headers=tp_env["h"]).json()["data"]
    assert detail["total"] == 3 and detail["passed"] == 1 and detail["failed"] == 1
    assert detail["blocked"] == 1 and detail["pending"] == 0
    # PASS 关联 Bug → 400
    resp = api_client.put(f"{_base(tp_env)}/{plan['id']}/items/{items[0]['id']}/execute",
                          headers=tp_env["h"], json={"result": "PASS", "issueId": issue["id"]})
    assert resp.status_code == 400


def test_completed_plan_rejects_execute_and_add(api_client, tp_env):
    plan = _create_plan(api_client, tp_env)
    _add_all(api_client, tp_env, plan["id"])
    assert api_client.put(f"{_base(tp_env)}/{plan['id']}", headers=tp_env["h"],
                          json={"status": "COMPLETED"}).status_code == 200
    items = api_client.get(f"{_base(tp_env)}/{plan['id']}/items", headers=tp_env["h"]).json()["data"]
    resp = api_client.put(f"{_base(tp_env)}/{plan['id']}/items/{items[0]['id']}/execute",
                          headers=tp_env["h"], json={"result": "PASS"})
    assert resp.status_code == 400
    resp = api_client.post(f"{_base(tp_env)}/{plan['id']}/items", headers=tp_env["h"],
                           json={"caseIds": tp_env["cases"]})
    assert resp.status_code == 400


def test_non_member_403_and_cross_project_404(api_client, admin_token, create_api_user, tp_env):
    plan = _create_plan(api_client, tp_env)
    outsider = create_api_user()
    h = auth_headers(api_client.post("/api/v1/auth/login",
                                     json={"username": outsider["username"],
                                           "password": outsider["password"]}).json()["data"]["accessToken"])
    # 非项目成员 → 数据级 403（先于目标查找）
    assert api_client.get(f"{_base(tp_env)}/{plan['id']}", headers=h).status_code == 403
    # 其他项目 owner 访问 → 404（目标不在该项目）
    import uuid
    su = uuid.uuid4().hex[:8].upper()
    org2 = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                           json={"name": f"P21TP 组织B {su}", "code": f"P21TPORGB{su}",
                                 "description": None}).json()["data"]
    proj2 = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                            json={"name": "P21TP 项目B", "key": f"P21TPB{su}",
                                  "orgId": org2["id"], "description": None}).json()["data"]
    resp = api_client.get(f"/api/v1/projects/{proj2['id']}/testplans/{plan['id']}",
                          headers=auth_headers(admin_token))
    assert resp.status_code == 404


def test_permission_matrix_401_403(api_client, create_api_user, tp_env):
    assert api_client.get(_base(tp_env)).status_code == 401
    user = create_api_user()
    h = auth_headers(api_client.post("/api/v1/auth/login",
                                     json={"username": user["username"],
                                           "password": user["password"]}).json()["data"]["accessToken"])
    assert api_client.get(_base(tp_env), headers=h).status_code == 403
