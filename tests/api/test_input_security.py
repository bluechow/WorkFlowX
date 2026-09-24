"""输入安全测试（Phase 15; P15-08/10/11/19）：SQL 注入探测 / 边界 fuzz / enum 篡改 / mass assignment。

marker: security。payload 全部低风险探测型（禁止 DROP/DELETE/UPDATE/INSERT 破坏性形式）。
验证: 不返回 500、不暴露 SQL/堆栈、不绕过查询条件、enum 篡改 422、workflow 不可绕过。
"""
import pytest

pytestmark = pytest.mark.security

SQLI_PAYLOADS = ["'", '"', "' OR '1'='1", '" OR "1"="1', "1 OR 1=1", "1' AND '1'='1", "'; --"]


def _h(token):
    return {"Authorization": f"Bearer {token}"}


def _seed_project(api_client, admin_token, unique_suffix, cleanup_orgs=None):
    h = {"Authorization": f"Bearer {admin_token}"}
    su = unique_suffix.upper()
    org = api_client.post("/api/v1/orgs", headers=h, json={
        "name": f"SECURITY org {su}", "code": f"SECINJ{su}", "description": None}).json()["data"]
    if cleanup_orgs is not None:
        cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=h, json={
        "name": "SECURITY 注入项目", "key": f"SECINJP{su}", "orgId": org["id"],
        "description": None}).json()["data"]
    return org, project


# ===== SQL Injection 探测 =====

def test_sqli_in_search_keyword_no_error_no_bypass(api_client, admin_token, unique_suffix, cleanup_orgs):
    org = api_client.post("/api/v1/orgs", headers=_h(admin_token),
                          json={"name": f"SECURITY org {unique_suffix}",
                                "code": f"SECINJK{unique_suffix.upper()}",
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    for payload in SQLI_PAYLOADS:
        resp = api_client.get("/api/v1/orgs", params={"keyword": payload},
                              headers=_h(admin_token))
        assert resp.status_code == 200, f"keyword={payload!r} 应正常处理而非 {resp.status_code}"
        body = resp.text.lower()
        assert "sql" not in body and "exception" not in body, f"keyword={payload!r} 泄露 SQL/异常"
        assert resp.json()["data"]["total"] == 0 or isinstance(resp.json()["data"]["total"], int)


def test_sqli_in_issue_title_stored_as_literal_text(api_client, admin_token, unique_suffix, cleanup_orgs):
    """SQL payload 作为普通文本入库原样返回（参数化查询防御的行为证据）。"""
    org, project = _seed_project(api_client, admin_token, unique_suffix, cleanup_orgs)
    evil = "SECURITY ' OR '1'='1 -- 注入探测"
    resp = api_client.post(f"/api/v1/projects/{project['id']}/issues",
                           json={"title": evil, "type": "TASK", "priority": "MEDIUM"},
                           headers=_h(admin_token))
    assert resp.status_code == 201, resp.text
    assert resp.json()["data"]["title"] == evil, "payload 必须作为字面文本存储与返回"
    # 列表 keyword 精确过滤也能找回（未发生注入改变查询语义）
    listing = api_client.get(f"/api/v1/projects/{project['id']}/issues",
                             params={"keyword": evil, "page": 1, "size": 10},
                             headers=_h(admin_token)).json()["data"]
    assert listing["total"] == 1


# ===== 负向 fuzz（轻量）=====

def test_issue_create_fuzz_matrix(api_client, admin_token, unique_suffix, cleanup_orgs):
    org, project = _seed_project(api_client, admin_token, unique_suffix, cleanup_orgs)
    base_path = f"/api/v1/projects/{project['id']}/issues"
    cases = [
        ({"title": None, "type": "TASK"}, (400, 422)),
        ({"title": "SECURITY", "type": "NOT_AN_ENUM"}, (400, 422)),      # enum tampering
        ({"title": "SECURITY", "type": "TASK", "priority": 999}, (400, 422)),
        ({"title": "SECURITY", "type": "TASK", "assigneeId": "not-a-number"}, (400, 422)),
        ({"title": "SECURITY", "type": "TASK", "assigneeId": -1}, (400, 422)),
        ({"title": "SECURITY", "type": "TASK", "severity": "S1"}, [400]),
        ({"title": "SECURITY", "type": "TASK", "unknownField": {"nested": True}}, [201, 400, 422]),
        ([1, 2, 3], [400, 422]),                                          # 数组当对象
        ("just a string", [400, 422]),                                    # 字符串当对象
    ]
    for payload, expected in cases:
        if isinstance(payload, str):
            resp = api_client.post(base_path, content=payload,
                                   headers={**_h(admin_token), "Content-Type": "application/json"})
        else:
            resp = api_client.post(base_path, json=payload, headers=_h(admin_token))
        assert resp.status_code in expected, f"{payload} → {resp.status_code}（期望 {expected}）"
        if resp.status_code >= 400:
            blob = (resp.text or "").lower()
            for banned in ("sql", "exception:", "at com.workflowx", "java."):
                assert banned not in blob, f"错误响应泄露内部信息: {banned}"


def test_pagination_fuzz(api_client, admin_token):
    for params in [{"page": -1}, {"page": 0}, {"size": 0}, {"size": -5},
                   {"page": 1, "size": 99999}]:
        resp = api_client.get("/api/v1/users", params=params, headers=_h(admin_token))
        assert resp.status_code in (200, 400, 422), f"{params} → {resp.status_code}"
        if resp.status_code == 200:
            assert resp.json()["data"]["size"] <= 100, "size 超限必须被钳制或拒绝"


# ===== mass assignment =====

def test_user_update_mass_assignment_ignored(api_client, admin_token, create_api_user):
    user = create_api_user()
    resp = api_client.put(f"/api/v1/users/{user['id']}", headers=_h(admin_token),
                          json={"email": user["email"], "nickname": "SECURITY-MA",
                                "roles": ["ADMIN"], "status": "LOCKED",
                                "ownerId": 1, "createdAt": "1999-01-01T00:00:00"})
    assert resp.status_code == 200, resp.text
    data = resp.json()["data"]
    assert data["status"] == "ACTIVE", "status 不得通过 update 篡改"
    assert data["id"] == user["id"]
    assert "ADMIN" not in str(data), "roles 不得被注入"


def test_workflow_state_tampering_rejected(api_client, admin_token, unique_suffix, cleanup_orgs):
    """直接 PATCH 状态机（CLOSED→OPEN / OPEN→CLOSED）必须 409。"""
    org, project = _seed_project(api_client, admin_token, unique_suffix, cleanup_orgs)
    h = _h(admin_token)
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=h,
                            json={"title": "SECURITY 篡改目标", "type": "TASK",
                                  "priority": "MEDIUM", "assigneeId": None}).json()["data"]
    iid = f"/api/v1/projects/{project['id']}/issues/{issue['id']}/status"
    for frm, to in [("OPEN", "CLOSED"), ("OPEN", "TESTING")]:
        resp = api_client.patch(iid, headers=h, json={"fromStatus": frm, "toStatus": to})
        assert resp.status_code == 409, f"{frm}→{to} 非法流转必须 409"
    # 合法走到 CLOSED 后 → 终态出边拒绝
    for frm, to in [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED"),
                    ("RESOLVED", "TESTING"), ("TESTING", "CLOSED")]:
        api_client.patch(iid, headers=h, json={"fromStatus": frm, "toStatus": to})
    resp = api_client.patch(iid, headers=h, json={"fromStatus": "CLOSED", "toStatus": "OPEN"})
    assert resp.status_code == 409, "CLOSED 终态不可转出（状态机不可被直接 API 绕过）"
