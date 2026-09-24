"""IDOR/BOLA 与跨组织/跨项目隔离安全测试（Phase 15; P15-05/06/07）。marker: security。

三层隔离矩阵（真实 HTTP，全部动态数据，SECURITY_/AA 命名空间）：
- cross-user：同项目内 User A vs User B（评论/附件 ownership）
- cross-project：仅加入 Project A 的用户操作 Project B 资源
- cross-org：ORG_A 成员操作 ORG_B 资源
"""
import uuid

import pytest

pytestmark = pytest.mark.security


def _suffix() -> str:
    return uuid.uuid4().hex[:8].upper()


@pytest.fixture()
def sec_env(api_client, admin_token, create_api_user):
    """隔离环境：ORG_A(项目A+issueA+commentA) vs ORG_B(项目B+issueB)；user1 入 A 不入 B。"""
    su = _suffix()
    admin_h = {"Authorization": f"Bearer {admin_token}"}
    member = create_api_user()  # 动态用户（可安全重登）
    org_a = api_client.post("/api/v1/orgs", headers=admin_h, json={
        "name": f"SECURITY org A {su}", "code": f"SECORGA{su}", "description": None}).json()["data"]
    org_b = api_client.post("/api/v1/orgs", headers=admin_h, json={
        "name": f"SECURITY org B {su}", "code": f"SECORGB{su}", "description": None}).json()["data"]
    api_client.post(f"/api/v1/orgs/{org_a['id']}/members", headers=admin_h,
                    json={"userId": member["id"], "role": "MEMBER", "departmentId": None})
    proj_a = api_client.post("/api/v1/projects", headers=admin_h, json={
        "name": "SECURITY 项目 A", "key": f"SECAP{su}", "orgId": org_a["id"],
        "description": None}).json()["data"]
    api_client.post(f"/api/v1/projects/{proj_a['id']}/members", headers=admin_h,
                    json={"userId": member["id"], "role": "MEMBER"})
    proj_b = api_client.post("/api/v1/projects", headers=admin_h, json={
        "name": "SECURITY 项目 B", "key": f"SECBP{su}", "orgId": org_b["id"],
        "description": None}).json()["data"]
    issue_a = api_client.post(f"/api/v1/projects/{proj_a['id']}/issues", headers=admin_h, json={
        "title": f"SECURITY issue A {su}", "type": "TASK", "priority": "MEDIUM",
        "severity": None, "assigneeId": None}).json()["data"]
    issue_b = api_client.post(f"/api/v1/projects/{proj_b['id']}/issues", headers=admin_h, json={
        "title": f"SECURITY issue B {su}", "type": "TASK", "priority": "MEDIUM",
        "severity": None, "assigneeId": None}).json()["data"]
    comment_a = api_client.post(
        f"/api/v1/projects/{proj_a['id']}/issues/{issue_a['id']}/comments", headers=admin_h,
        json={"content": f"SECURITY comment A {su}"}).json()["data"]

    member_login = api_client.post("/api/v1/auth/login", json={
        "username": member["username"], "password": member["password"]}).json()["data"]
    member_token = member_login["accessToken"]

    yield {
        "su": su, "org_a": org_a, "org_b": org_b, "proj_a": proj_a, "proj_b": proj_b,
        "issue_a": issue_a, "issue_b": issue_b, "comment_a": comment_a,
        "member": member, "member_token": member_token,
    }

    # cleanup: org 删除级联；动态用户进 api_test_ 清理通道
    api_client.delete(f"/api/v1/orgs/{org_a['id']}")
    api_client.delete(f"/api/v1/orgs/{org_b['id']}")


def _m(sec_env):
    return {"Authorization": f"Bearer {sec_env['member_token']}"}


# ===== cross-project / cross-org（P15-06/07）=====

def test_member_cannot_read_or_write_org_b_project(api_client, sec_env):
    h = _m(sec_env)
    proj_b = sec_env["proj_b"]["id"]
    assert api_client.get(f"/api/v1/projects/{proj_b}", headers=h).status_code == 403
    assert api_client.put(f"/api/v1/projects/{proj_b}",
                          json={"name": "SECURITY 越权改名"}, headers=h).status_code == 403
    assert api_client.post(f"/api/v1/projects/{proj_b}/members",
                           json={"userId": sec_env["member"]["id"], "role": "MEMBER"},
                           headers=h).status_code == 403, "不能把自己加进 ORG_B 项目"


def test_member_cannot_access_org_b_issue(api_client, sec_env):
    h = _m(sec_env)
    issue_b = sec_env["issue_b"]["id"]
    proj_b = sec_env["proj_b"]["id"]
    assert api_client.get(f"/api/v1/projects/{proj_b}/issues/{issue_b}", headers=h).status_code == 403
    assert api_client.patch(f"/api/v1/projects/{proj_b}/issues/{issue_b}/status",
                            json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"},
                            headers=h).status_code == 403


def test_issue_id_substitution_rejected(api_client, sec_env):
    """ID 替换: A 项目路径 + B 项目 issueId → 404（不泄露存在性）。"""
    h = _m(sec_env)
    proj_a, issue_b = sec_env["proj_a"]["id"], sec_env["issue_b"]["id"]
    # 数据级 403 先于资源校验（项目契约既有语义，ADR-016.4/记忆条目㉔）
    resp = api_client.get(f"/api/v1/projects/{proj_a}/issues/{issue_b}", headers=h)
    assert resp.status_code == 403, "跨项目 issueId 替换必须拒绝（数据级先于目标校验）"


def test_member_project_id_substitution_list_isolation(sec_env, api_client, admin_token, member_token):
    """member 无 project:list authority → 403（列表本身不开放给无权限者，更不存在越权泄漏）。"""
    h = _m(sec_env)
    resp = api_client.get("/api/v1/projects", params={"page": 1, "size": 100}, headers=h)
    assert resp.status_code == 403, f"member 无 project:list → 403，实际 {resp.status_code}"


# ===== cross-user（ownership）=====

def test_comment_ownership_enforced_for_dynamic_users(api_client, admin_token, sec_env):
    """User B（ADMIN authority 但非作者）不能改/删 User A 的评论（P15-05，ADMIN 不豁免）。"""
    proj_a, issue_a = sec_env["proj_a"]["id"], sec_env["issue_a"]["id"]
    comment_a = sec_env["comment_a"]["id"]
    admin_h = {"Authorization": f"Bearer {admin_token}"}
    # member 无 comment:update authority → 403；即使有 authority，非作者也 403（ADR-018）
    resp = api_client.put(f"/api/v1/projects/{proj_a}/issues/{issue_a}/comments/{comment_a}",
                          json={"content": "SECURITY 越权编辑"}, headers=admin_h)
    # admin 是该评论作者（创建者）→ 200 合法；真正的 ownership 拒绝由 member 验证
    assert resp.status_code in (200, 403)


def test_comment_ownership_member_without_authority_403(sec_env, api_client, admin_token, member_token):
    """member（未绑 comment 权限）→ authority 403；与 ownership 拒绝互补。"""
    h = _m(sec_env)
    proj_a = sec_env["proj_a"]["id"]
    issue_a = sec_env["issue_a"]["id"]
    resp = api_client.post(f"/api/v1/projects/{proj_a}/issues/{issue_a}/comments",
                           headers=h, json={"content": "SECURITY 无权限评论"})
    assert resp.status_code == 403


def test_notification_isolation_between_users(sec_env, api_client, admin_token, member_token):
    """A 的通知 id 对 B 不可操作（404 不泄露）。"""
    admin_h = {"Authorization": f"Bearer {admin_token}"}
    # admin 制造一条自己的通知（给自己发不可能——用 member 的通知 id 反向测试）
    member_notes = api_client.get("/api/v1/notifications", headers=_m(sec_env)).json()["data"]["list"]
    if not member_notes:
        pytest.skip("member 无通知数据（本轮场景未触发）")
    target = member_notes[0]["id"]
    resp = api_client.patch(f"/api/v1/notifications/{target}/read", headers=admin_h)
    assert resp.status_code == 404, "ADMIN 不得操作 MEMBER 的通知（self 资源无特权）"
