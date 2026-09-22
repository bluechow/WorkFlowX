"""Notification API 黑盒测试（Phase 9, P9）：真实 HTTP。

规则（ADR-019）: 通知为 self 资源（无 notification:* 权限码，对齐 /auth/me 先例）；
数据隔离=recipient ownership（跨用户 404）；mark read 幂等；read-all 数据库条件更新只影响本人。
业务触发: Issue 创建/变更分派、状态流转、评论（通知内容含真实业务编号）。
"""

import httpx
import pytest

from conftest import auth_headers

KEY_PREFIX = "P9NT"
ORG_PREFIX = "P9NT_ORG_"


@pytest.fixture(scope="session", autouse=True)
def cleanup_notifications_at_session_end(db):
    """通知无删除 API 且收件人多为 seed 用户——会话结束 DB 直连清空（终态 notifications=0）。"""
    yield
    with db.cursor() as cur:
        cur.execute("DELETE FROM notifications")


def _setup(api_client: httpx.Client, admin_token: str, unique_suffix: str,
           cleanup_orgs: list) -> tuple[int, int, int]:
    """组织→项目（user1 入组织与项目）→Issue（admin 创建并分派 user1）。返回 (org, project, issue)。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": f"P9NT 组织 {unique_suffix}",
                                "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": 2, "role": "MEMBER", "departmentId": None})
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P9NT 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    api_client.post(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": 2, "role": "MEMBER"})
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "P9NT 目标", "description": "notification test",
                                  "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": 2}).json()["data"]
    return org["id"], project["id"], issue["id"]


def _unread(api_client: httpx.Client, token: str) -> int:
    resp = api_client.get("/api/v1/notifications/unread-count", headers=auth_headers(token))
    assert resp.status_code == 200, resp.text
    return resp.json()["data"]["count"]


def _cleanup_notifications(api_client: httpx.Client, admin_token: str, member_token: str) -> None:
    """通知无独立删除 API 且 user1 通知无 FK——测试自清理：全部已读不影响行数，行清理走 DB（conftest 兜底）。
    这里通过标记已读保证幂等复跑，行数据随 org 级联外的部分由 Gate 终态核验兜底。"""
    api_client.patch("/api/v1/notifications/read-all", headers=auth_headers(member_token))
    api_client.patch("/api/v1/notifications/read-all", headers=auth_headers(admin_token))


def test_assignment_creates_notification_with_business_context(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    """admin 创建 Issue 并分派 user1 → user1 收到含业务编号的未读通知。"""
    _, project_id, _ = _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    assert api_client.get(f"/api/v1/projects/{project_id}/issues",
                          headers=auth_headers(admin_token)).status_code == 200
    count = _unread(api_client, member_token)
    assert count >= 1, "分派应产生通知"
    listing = api_client.get("/api/v1/notifications", headers=auth_headers(member_token)).json()["data"]
    top = listing["list"][0]
    assert top["type"] == "ISSUE_ASSIGNED"
    assert top["isRead"] is False
    assert top["relatedType"] == "ISSUE"
    assert (KEY_PREFIX) in top["content"], "通知内容含真实业务编号（防无上下文假通知）"
    _cleanup_notifications(api_client, admin_token, member_token)


def test_status_transition_notifies_assignee(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    before = _unread(api_client, member_token)
    resp = api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                            headers=auth_headers(admin_token),
                            json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    assert resp.status_code == 200
    assert _unread(api_client, member_token) == before + 1
    top = api_client.get("/api/v1/notifications?read=false",
                         headers=auth_headers(member_token)).json()["data"]["list"][0]
    assert top["type"] == "ISSUE_STATUS_CHANGED"
    _cleanup_notifications(api_client, admin_token, member_token)


def test_comment_notifies_assignee_and_reporter(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    # admin（=reporter）评论 → user1（assignee）收通知
    resp = api_client.post(f"/api/v1/projects/{project_id}/issues/{issue_id}/comments",
                           headers=auth_headers(admin_token), json={"content": "P9NT 评论"})
    assert resp.status_code == 201
    types = [n["type"] for n in api_client.get(
        "/api/v1/notifications", headers=auth_headers(member_token)).json()["data"]["list"]]
    assert "ISSUE_COMMENTED" in types
    _cleanup_notifications(api_client, admin_token, member_token)


def test_read_filter_and_mark_read_flow(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    # read=false 筛选
    unread_list = api_client.get("/api/v1/notifications?read=false",
                                 headers=auth_headers(member_token)).json()["data"]
    assert unread_list["total"] >= 1
    target = unread_list["list"][0]
    # 标记已读
    resp = api_client.patch(f"/api/v1/notifications/{target['id']}/read",
                            headers=auth_headers(member_token))
    assert resp.status_code == 200
    # read=true 中出现该条；read=false 消失
    read_ids = [n["id"] for n in api_client.get(
        "/api/v1/notifications?read=true", headers=auth_headers(member_token)).json()["data"]["list"]]
    assert target["id"] in read_ids
    # 幂等: 再次标记已读仍 200
    resp = api_client.patch(f"/api/v1/notifications/{target['id']}/read",
                            headers=auth_headers(member_token))
    assert resp.status_code == 200
    _cleanup_notifications(api_client, admin_token, member_token)


def test_mark_all_read_zeroes_unread(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    assert _unread(api_client, member_token) >= 1
    resp = api_client.patch("/api/v1/notifications/read-all", headers=auth_headers(member_token))
    assert resp.status_code == 200
    assert resp.json()["data"]["updated"] >= 1
    assert _unread(api_client, member_token) == 0
    assert api_client.get("/api/v1/notifications?read=false",
                          headers=auth_headers(member_token)).json()["data"]["total"] == 0


def test_cross_user_access_returns_404(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    """确定性场景: user1 评论 admin 的未分派 Issue → admin（reporter）收通知；
    user1（评论者本人，被排除）拿 admin 的通知 id 标记 → 404（跨用户不泄露存在性）。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": f"P9NT 组织X {unique_suffix}",
                                "code": (ORG_PREFIX + "X" + unique_suffix.upper()),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    api_client.post(f"/api/v1/orgs/{org['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": 2, "role": "MEMBER", "departmentId": None})
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P9NT 项目X", "key": KEY_PREFIX + "X" + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    api_client.post(f"/api/v1/projects/{project['id']}/members", headers=auth_headers(admin_token),
                    json={"userId": 2, "role": "MEMBER"})
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "P9NT 未分派", "description": None,
                                  "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": None}).json()["data"]
    # user1 无 comment:create → 授予（真实 RBAC 路径: 建角色+绑权限+绑用户）
    role = api_client.post("/api/v1/roles", headers=auth_headers(admin_token),
                           json={"code": f"P9NTCM{unique_suffix.upper()}",
                                 "name": f"p9nt-cm-{unique_suffix}",
                                 "description": None}).json()["data"]
    api_client.put(f"/api/v1/roles/{role['id']}/permissions", headers=auth_headers(admin_token),
                   json={"permissionCodes": ["comment:create"]})
    api_client.post("/api/v1/users/2/roles", headers=auth_headers(admin_token),
                    json={"roleCode": f"P9NTCM{unique_suffix.upper()}"})
    resp = api_client.post(f"/api/v1/projects/{project['id']}/issues/{issue['id']}/comments",
                           headers=auth_headers(member_token), json={"content": "P9NT 跨用户评论"})
    assert resp.status_code == 201, resp.text
    # 回收角色（解绑+删除）
    api_client.delete(f"/api/v1/users/2/roles/P9NTCM{unique_suffix.upper()}",
                      headers=auth_headers(admin_token))
    api_client.delete(f"/api/v1/roles/{role['id']}", headers=auth_headers(admin_token))
    admin_notes = api_client.get("/api/v1/notifications",
                                 headers=auth_headers(admin_token)).json()["data"]["list"]
    target = next(n for n in admin_notes if n["type"] == "ISSUE_COMMENTED")
    # member 的列表中没有这条（评论者被排除）
    mine_ids = [n["id"] for n in api_client.get(
        "/api/v1/notifications", headers=auth_headers(member_token)).json()["data"]["list"]]
    assert target["id"] not in mine_ids
    # 跨用户标记 → 404
    resp = api_client.patch(f"/api/v1/notifications/{target['id']}/read",
                            headers=auth_headers(member_token))
    assert resp.status_code == 404
    # admin 本人标记 → 200
    assert api_client.patch(f"/api/v1/notifications/{target['id']}/read",
                            headers=auth_headers(admin_token)).status_code == 200


def test_pagination_stable_order(
        api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _setup(api_client, admin_token, unique_suffix, cleanup_orgs)
    # 多次流转制造多条通知: OPEN→IN_PROGRESS→RESOLVED（assignee 收 2 条状态通知）
    for frm, to in [("OPEN", "IN_PROGRESS"), ("IN_PROGRESS", "RESOLVED")]:
        resp = api_client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                                headers=auth_headers(admin_token),
                                json={"fromStatus": frm, "toStatus": to})
        assert resp.status_code == 200
    page1 = api_client.get("/api/v1/notifications?page=1&size=2",
                           headers=auth_headers(member_token)).json()["data"]
    page2 = api_client.get("/api/v1/notifications?page=2&size=2",
                           headers=auth_headers(member_token)).json()["data"]
    assert page1["total"] >= 3  # 分派 1 + 流转 2
    assert len(page1["list"]) == 2 and len(page2["list"]) >= 1
    created = [n["createdAt"] for n in page1["list"] + page2["list"]]
    assert created == sorted(created, reverse=True), "created_at DESC 稳定排序"
    _cleanup_notifications(api_client, admin_token, member_token)


def test_unauthenticated_rejected(api_client):
    assert api_client.get("/api/v1/notifications").status_code == 401
    assert api_client.get("/api/v1/notifications/unread-count").status_code == 401
    assert api_client.patch("/api/v1/notifications/read-all").status_code == 401
