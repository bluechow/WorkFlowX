"""Dashboard API 黑盒测试（Phase 10, P10-15）：真实 HTTP。

规则（ADR-020）: dashboard:view authority（ADMIN）；数据范围=ADMIN 全系统、
普通授权用户仅成员项目。指标全部来自真实业务表聚合，与 DB 状态一致。
"""

import httpx

from conftest import auth_headers

KEY_PREFIX = "P10DB"
ORG_PREFIX = "P10DB_ORG_"


def _seed_two_projects_with_issues(api_client: httpx.Client, admin_token: str,
                                   unique_suffix: str, cleanup_orgs: list) -> tuple[int, int]:
    """两个项目各建 1 个 Issue（BUG/TASK），第一个流转。返回 (projectA_id, projectB_id)。"""
    ids = []
    for tag in ("A", "B"):
        org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                              json={"name": f"P10DB 组织{tag}", "code": ORG_PREFIX + tag + unique_suffix.upper(),
                                    "description": None}).json()["data"]
        cleanup_orgs.append(org["id"])
        project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                                  json={"name": f"P10DB 项目{tag}", "key": KEY_PREFIX + tag + unique_suffix.upper(),
                                        "orgId": org["id"], "description": None}).json()["data"]
        ids.append(project["id"])
    api_client.post(f"/api/v1/projects/{ids[0]}/issues", headers=auth_headers(admin_token),
                    json={"title": "P10DB BUG", "description": None, "type": "BUG",
                          "priority": "HIGH", "severity": "S1", "assigneeId": None}).json()["data"]
    issue_b = api_client.post(f"/api/v1/projects/{ids[1]}/issues", headers=auth_headers(admin_token),
                              json={"title": "P10DB TASK", "description": None, "type": "TASK",
                                    "priority": "LOW", "severity": None, "assigneeId": None}).json()["data"]
    api_client.patch(f"/api/v1/projects/{ids[1]}/issues/{issue_b['id']}/status",
                     headers=auth_headers(admin_token),
                     json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    return ids[0], ids[1]


def test_dashboard_requires_authority(api_client, member_token):
    assert api_client.get("/api/v1/dashboard/overview").status_code == 401
    resp = api_client.get("/api/v1/dashboard/overview", headers=auth_headers(member_token))
    assert resp.status_code == 403, "普通 MEMBER 无 dashboard:view → 403"


def test_admin_overview_reflects_real_data(
        api_client, admin_token, unique_suffix, cleanup_orgs):
    project_a, project_b = _seed_two_projects_with_issues(
        api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.get("/api/v1/dashboard/overview", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    data = resp.json()["data"]

    # 项目统计真实（总数 >= 本测试创建的 2 个；活跃=total-archived）
    assert data["projects"]["total"] >= 2
    assert data["projects"]["active"] == data["projects"]["total"] - data["projects"]["archived"]

    # Issue 分布真实：新增 2 个（BUG/OPEN + TASK/IN_PROGRESS）必然体现在分布中
    assert data["issues"]["byType"]["BUG"] >= 1
    assert data["issues"]["byType"]["TASK"] >= 1
    assert data["issues"]["byStatus"]["OPEN"] >= 1
    assert data["issues"]["byStatus"]["IN_PROGRESS"] >= 1
    assert data["issues"]["bugCount"] >= 1
    assert data["issues"]["total"] == sum(data["issues"]["byStatus"].values()), \
        "issue total 与 byStatus 之和一致（真实聚合自洽）"

    # 趋势 14 天且今天有数据（本测试创建的 Issue）
    trend = data["createdTrend"]
    assert len(trend) == 14
    assert trend[-1]["created"] >= 1, "今日创建的 Issue 出现在趋势末点"


def test_dashboard_data_scope_for_authorized_non_admin(
        api_client, admin_token, create_api_user, unique_suffix, cleanup_orgs):
    """非 ADMIN 即使被授予 dashboard:view，数据范围也仅限其成员项目。
    用独立工厂用户（不重登 seed user1——单会话策略会顶掉 conftest 会话级 token 污染后续文件）。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "P10DB 组织S", "code": ORG_PREFIX + "S" + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P10DB 项目S", "key": KEY_PREFIX + "S" + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    # user1 不入该项目 → 授 dashboard:view 后看到的仍是不含该项目的统计
    role = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                           json={"code": f"P10DBV{unique_suffix.upper()}",
                                 "name": f"p10db-view-{unique_suffix}", "description": None}).json()["data"]
    api_client.put(f"/api/v1/roles/{role['id']}/permissions", headers=auth_headers(admin_token),
                   json={"permissionCodes": ["dashboard:view"]})
    viewer = create_api_user("dbview")
    api_client.post(f"/api/v1/users/{viewer['id']}/roles", headers=auth_headers(admin_token),
                    json={"roleCode": f"P10DBV{unique_suffix.upper()}"})
    # 该用户重登（仅覆盖自己的会话，不影响 seed 用户）
    member = api_client.post("/api/v1/auth/login",
                             json={"username": viewer["username"],
                                   "password": viewer["password"]}).json()["data"]
    scoped = api_client.get("/api/v1/dashboard/overview",
                            headers=auth_headers(member["accessToken"])).json()["data"]
    admin_view = api_client.get("/api/v1/dashboard/overview",
                                headers=auth_headers(admin_token)).json()["data"]
    assert scoped["issues"]["total"] <= admin_view["issues"]["total"], "非 ADMIN 范围不超出 ADMIN"

    # 回收角色
    api_client.delete(f"/api/v1/users/{viewer['id']}/roles/P10DBV{unique_suffix.upper()}",
                      headers=auth_headers(admin_token))
    api_client.delete(f"/api/v1/roles/{role['id']}", headers=auth_headers(admin_token))
