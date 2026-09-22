"""Audit API 黑盒测试（Phase 10, P10-15）：真实 HTTP。

规则（ADR-020）: audit_logs 为高敏感系统资源——audit:list/get authority（仅 ADMIN）；
普通用户/未认证一律拒绝。审计由真实业务触发（登录/项目/Issue 等），数据与 DB 一致。
"""

import httpx

from conftest import auth_headers

KEY_PREFIX = "P10AU"
ORG_PREFIX = "P10AU_ORG_"


def test_audit_requires_admin_authority(api_client, member_token):
    """普通 MEMBER 无 audit:list → 403；未认证 → 401。"""
    assert api_client.get("/api/v1/audit-logs").status_code == 401
    resp = api_client.get("/api/v1/audit-logs", headers=auth_headers(member_token))
    assert resp.status_code == 403, "无 authority 的普通用户拒绝（不允许任何数据范围）"


def test_admin_can_list_audit_with_business_facts(
        api_client, admin_token, unique_suffix, cleanup_orgs):
    """admin 执行真实业务（建组织/项目/Issue）→ 审计列表含对应操作事实。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": "P10AU 组织", "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P10AU 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "P10AU 目标", "description": "audit",
                                  "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": None}).json()["data"]
    api_client.patch(f"/api/v1/projects/{project['id']}/issues/{issue['id']}/status",
                     headers=auth_headers(admin_token),
                     json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})

    listing = api_client.get("/api/v1/audit-logs?module=ISSUE&action=TRANSITION",
                             headers=auth_headers(admin_token)).json()["data"]
    assert listing["total"] >= 1
    top = listing["list"][0]
    assert top["target"] == f"issue:{issue['id']}"
    assert "OPEN -> IN_PROGRESS" in top["summary"]
    assert top["success"] is True
    assert top["userId"] == 1, "操作者为真实执行者 admin"
    assert top["traceId"], "审计含 traceId 链路字段"


def test_audit_filters_and_pagination(api_client, admin_token, unique_suffix, cleanup_orgs):
    listing = api_client.get("/api/v1/audit-logs?module=AUTH&size=5",
                             headers=auth_headers(admin_token)).json()["data"]
    assert listing["size"] == 5
    created = [row["createdAt"] for row in listing["list"]]
    assert created == sorted(created, reverse=True), "created_at DESC 稳定排序"
    # operator 筛选
    mine = api_client.get("/api/v1/audit-logs?operator=1&size=3",
                          headers=auth_headers(admin_token)).json()["data"]
    assert all(row["userId"] == 1 for row in mine["list"])
    # success 筛选
    failures = api_client.get("/api/v1/audit-logs?success=false&size=3",
                              headers=auth_headers(admin_token)).json()["data"]
    assert all(row["success"] is False for row in failures["list"])


def test_audit_by_id(api_client, admin_token, member_token, unique_suffix):
    listing = api_client.get("/api/v1/audit-logs?size=1",
                             headers=auth_headers(admin_token)).json()["data"]
    if listing["total"] == 0:
        return
    audit_id = listing["list"][0]["id"]
    resp = api_client.get(f"/api/v1/audit-logs/{audit_id}", headers=auth_headers(admin_token))
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == audit_id
    # member 无 audit:get → 403（不能通过 ID 枚举读取任何审计）
    resp = api_client.get(f"/api/v1/audit-logs/{audit_id}", headers=auth_headers(member_token))
    assert resp.status_code == 403
    # 不存在的 id → 404
    resp = api_client.get("/api/v1/audit-logs/999999999", headers=auth_headers(admin_token))
    assert resp.status_code == 404


def test_login_failure_is_audited_without_password(api_client, admin_token):
    """登录失败被审计（独立事务幸存）；审计内容绝不含密码。"""
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": "p10au_nouser", "password": "WrongPass@123"})
    assert resp.status_code == 401
    listing = api_client.get("/api/v1/audit-logs?module=AUTH&action=LOGIN_FAIL&size=5",
                             headers=auth_headers(admin_token)).json()["data"]
    assert listing["total"] >= 1, "登录失败审计幸存（REQUIRES_NEW）"
    for row in listing["list"]:
        blob = str(row["summary"]) + str(row["target"]) + str(row["uri"])
        assert "WrongPass@123" not in blob, "密码绝不出现在审计内容"
        assert "password" not in blob.lower()
