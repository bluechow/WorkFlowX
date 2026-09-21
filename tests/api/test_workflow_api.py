"""Issue Workflow API 黑盒测试（Phase 7, P7-07）：真实 HTTP。

正式矩阵（ADR-017）: OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED；
TESTING→REOPENED；REOPENED→IN_PROGRESS。非法/同状态/并发覆盖 → 409。
"""

import httpx

from conftest import PASSWORD, auth_headers

KEY_PREFIX = "P7WF"
ORG_PREFIX = "P7WF_ORG_"


def _create_org_project_issue(api_client: httpx.Client, admin_token: str, unique_suffix: str,
                              cleanup_orgs: list) -> tuple[int, int, int]:
    """组织→项目→Issue 三级夹具，返回 (org_id, project_id, issue_id)。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": f"WF 组织 {unique_suffix}",
                                "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "WF 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "WF 目标", "description": "workflow test",
                                  "type": "BUG", "priority": "HIGH", "severity": "S1",
                                  "assigneeId": None}).json()["data"]
    return org["id"], project["id"], issue["id"]


def _transition(api_client: httpx.Client, token: str, project_id: int, issue_id: int,
                from_status: str, to_status: str):
    return api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            headers=auth_headers(token),
                            json={"fromStatus": from_status, "toStatus": to_status})


def test_initial_status_is_open(api_client: httpx.Client, admin_token: str, unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "OPEN"


def test_full_legal_main_chain(api_client: httpx.Client, admin_token: str, unique_suffix: str, cleanup_orgs):
    """主链: OPEN→IN_PROGRESS→RESOLVED→TESTING→CLOSED，逐步断言。"""
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    chain = [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED"),
             ("RESOLVED", "TESTING"), ("TESTING", "CLOSED")]
    for from_status, to_status in chain:
        resp = _transition(api_client, admin_token, project_id, issue_id, from_status, to_status)
        assert resp.status_code == 200, f"{from_status}→{to_status} 应合法: {resp.text}"
        assert resp.json()["data"]["status"] == to_status


def test_reopen_loop_from_testing(api_client: httpx.Client, admin_token: str, unique_suffix: str, cleanup_orgs):
    """TESTING→REOPENED→IN_PROGRESS 失败回路。"""
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    for from_status, to_status in [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED"),
                                   ("RESOLVED", "TESTING"), ("TESTING", "REOPENED")]:
        resp = _transition(api_client, admin_token, project_id, issue_id, from_status, to_status)
        assert resp.status_code == 200
    assert api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}",
                          headers=auth_headers(admin_token)).json()["data"]["status"] == "REOPENED"
    resp = _transition(api_client, admin_token, project_id, issue_id, "REOPENED", "IN_PROGRESS")
    assert resp.status_code == 200


def test_illegal_transitions_return_409(api_client: httpx.Client, admin_token: str,
                                        unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    # OPEN 出发仅允许 IN_PROGRESS：CLOSED/RESOLVED/TESTING/REOPENED 全部 409
    for target in ["CLOSED", "RESOLVED", "TESTING", "REOPENED"]:
        resp = _transition(api_client, admin_token, project_id, issue_id, "OPEN", target)
        assert resp.status_code == 409, f"OPEN→{target} 应为非法流转"
        body = resp.json()
        assert body["code"] == 409
        assert "非法状态流转" in body["message"]


def test_closed_is_terminal_no_outbound(api_client: httpx.Client, admin_token: str,
                                        unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    for from_status, to_status in [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED"),
                                   ("RESOLVED", "TESTING"), ("TESTING", "CLOSED")]:
        assert _transition(api_client, admin_token, project_id, issue_id, from_status, to_status).status_code == 200
    # CLOSED 无出边：任何目标都 409
    for target in ["OPEN", "IN_PROGRESS", "RESOLVED", "TESTING", "REOPENED"]:
        resp = _transition(api_client, admin_token, project_id, issue_id, "CLOSED", target)
        assert resp.status_code == 409, f"CLOSED→{target} 应为非法（终态）"


def test_self_transition_rejected_409(api_client: httpx.Client, admin_token: str,
                                      unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = _transition(api_client, admin_token, project_id, issue_id, "OPEN", "OPEN")
    assert resp.status_code == 409
    assert resp.json()["code"] == 409


def test_invalid_enum_returns_422(api_client: httpx.Client, admin_token: str,
                                  unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            headers=auth_headers(admin_token),
                            json={"fromStatus": "OPEN", "toStatus": "NOT_A_STATUS"})
    assert resp.status_code == 422


def test_no_token_returns_401(api_client: httpx.Client, admin_token: str, unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    assert resp.status_code == 401


def test_no_transition_permission_returns_403(api_client: httpx.Client, member_token: str, admin_token: str,
                                              unique_suffix: str, cleanup_orgs):
    _, project_id, issue_id = _create_org_project_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            headers=auth_headers(member_token),
                            json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    assert resp.status_code == 403


def test_issue_not_found_returns_404(api_client: httpx.Client, admin_token: str):
    resp = api_client.patch("/api/v1/projects/999999999/issues/999999999/status",
                            headers=auth_headers(admin_token),
                            json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    assert resp.status_code == 404


def test_concurrent_transitions_only_one_succeeds(
        api_client: httpx.Client, admin_token: str, unique_suffix: str, create_api_user, cleanup_orgs):
    """并发: 8 请求同时 OPEN→IN_PROGRESS——至多 1 成功（条件 UPDATE），其余 409。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "并发组织", "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "并发项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "并发目标", "description": "conc", "type": "BUG",
                                  "priority": "HIGH", "severity": "S1", "assigneeId": None}).json()["data"]

    import concurrent.futures
    body = {"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(api_client.patch,
                               f"/api/v1/projects/{project['id']}/issues/{issue['id']}/status",
                               headers=auth_headers(admin_token), json=body) for _ in range(8)]
        responses = [f.result() for f in futures]

    statuses = sorted(r.status_code for r in responses)
    assert statuses.count(200) == 1, f"仅 1 个 200，实际: {statuses}"
    assert statuses.count(409) == 7, f"其余 7 个 409，实际: {statuses}"
    final = api_client.get(f"/api/v1/projects/{project['id']}/issues/{issue['id']}",
                           headers=auth_headers(admin_token)).json()["data"]["status"]
    assert final == "IN_PROGRESS"


def test_transition_after_reopen_reenters_workflow(api_client: httpx.Client, admin_token: str,
                                                   unique_suffix: str, create_api_user, cleanup_orgs):
    """完整主链+REOPENED 回路+再次流转至 CLOSED。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "回路组织", "code": ORG_PREFIX + unique_suffix.upper() + "R",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "回路项目", "key": KEY_PREFIX + unique_suffix.upper() + "R",
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "回路目标", "description": "loop", "type": "BUG",
                                  "priority": "HIGH", "severity": "S2", "assigneeId": None}).json()["data"]
    pid, iid = project["id"], issue["id"]
    chain = [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED"),
             ("RESOLVED", "TESTING"), ("TESTING", "REOPENED"), ("REOPENED", "IN_PROGRESS"),
             ("IN_PROGRESS", "RESOLVED"), ("RESOLVED", "TESTING"), ("TESTING", "CLOSED")]
    for from_status, to_status in chain:
        resp = _transition_local(api_client, admin_token, pid, iid, from_status, to_status)
        assert resp.status_code == 200, f"{from_status}→{to_status} 应合法"
    assert api_client.get(f"/api/v1/projects/{pid}/issues/{iid}",
                          headers=auth_headers(admin_token)).json()["data"]["status"] == "CLOSED"


def _transition_local(api_client, admin_token, project_id, issue_id, from_status, to_status):
    return api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            headers=auth_headers(admin_token),
                            json={"fromStatus": from_status, "toStatus": to_status})
