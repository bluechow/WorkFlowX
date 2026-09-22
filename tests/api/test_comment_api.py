"""Issue Comment API 黑盒测试（Phase 8, P8-13）：真实 HTTP。

规则（ADR-018）: 权限三层 = comment:* authority + 项目成员数据级 + 作者本人 ownership。
author 服务端绑定不可伪造；跨项目/跨 Issue 拼接一律 404；无软删除。
"""

import httpx

from conftest import PASSWORD, auth_headers

KEY_PREFIX = "P8CM"
ORG_PREFIX = "P8CM_ORG_"
CONTENT_PREFIX = "P8CM 评论"


def _create_issue(api_client: httpx.Client, admin_token: str, unique_suffix: str,
                  cleanup_orgs: list) -> tuple[int, int, int]:
    """组织→项目→Issue 夹具，返回 (org_id, project_id, issue_id)。"""
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": f"P8CM 组织 {unique_suffix}",
                                "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P8CM 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "P8CM 目标", "description": "comment test",
                                  "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": None}).json()["data"]
    return org["id"], project["id"], issue["id"]


def _grant_member(api_client: httpx.Client, admin_token: str, org_id: int, project_id: int,
                  user_id: int = 2) -> None:
    """user1(seed MEMBER) 加入组织与项目（ADR-015 组织成员前置）。"""
    api_client.post(f"/api/v1/orgs/{org_id}/members", headers=auth_headers(admin_token),
                    json={"userId": user_id, "role": "MEMBER", "departmentId": None})
    api_client.post(f"/api/v1/projects/{project_id}/members", headers=auth_headers(admin_token),
                    json={"userId": user_id, "role": "MEMBER"})


def _create_comment(api_client: httpx.Client, token: str, project_id: int, issue_id: int,
                    content: str) -> httpx.Response:
    return api_client.post(f"/api/v1/projects/{project_id}/issues/{issue_id}/comments",
                           headers=auth_headers(token), json={"content": content})


def test_create_binds_author_to_operator(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = _create_comment(api_client, admin_token, project_id, issue_id, CONTENT_PREFIX + "-作者绑定")
    assert resp.status_code == 201, resp.text
    body = resp.json()["data"]
    assert body["authorId"] == 1, "author 自动绑定操作者 admin(id=1)"
    assert body["content"].startswith(CONTENT_PREFIX)


def test_list_orders_by_created_at_asc(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    for i in range(3):
        assert _create_comment(api_client, admin_token, project_id, issue_id,
                               f"{CONTENT_PREFIX}-序{i}").status_code == 201
    resp = api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}/comments",
                          headers=auth_headers(admin_token))
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["total"] == 3
    created = [c["createdAt"] for c in data["list"]]
    assert created == sorted(created), "评论按时间正序"


def test_update_own_comment(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    created = _create_comment(api_client, admin_token, project_id, issue_id,
                              CONTENT_PREFIX + "-待编辑").json()["data"]
    resp = api_client.put(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/comments/{created['id']}",
        headers=auth_headers(admin_token), json={"content": CONTENT_PREFIX + "-编辑后"})
    assert resp.status_code == 200, resp.text
    assert resp.json()["data"]["content"] == CONTENT_PREFIX + "-编辑后"


def test_member_cannot_update_others_comment(api_client, admin_token, member_token,
                                             unique_suffix, cleanup_orgs):
    org_id, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    _grant_member(api_client, admin_token, org_id, project_id)
    created = _create_comment(api_client, admin_token, project_id, issue_id,
                              CONTENT_PREFIX + "-作者评论").json()["data"]
    resp = api_client.put(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/comments/{created['id']}",
        headers=auth_headers(member_token), json={"content": CONTENT_PREFIX + "-越权"})
    assert resp.status_code == 403, "项目成员但非作者 → ownership 403"
    assert resp.json()["code"] == 403


def test_member_cannot_delete_others_comment(api_client, admin_token, member_token,
                                             unique_suffix, cleanup_orgs):
    org_id, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    _grant_member(api_client, admin_token, org_id, project_id)
    created = _create_comment(api_client, admin_token, project_id, issue_id,
                              CONTENT_PREFIX + "-不可删").json()["data"]
    resp = api_client.delete(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/comments/{created['id']}",
        headers=auth_headers(member_token))
    assert resp.status_code == 403


def test_author_delete_is_real_removal(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    created = _create_comment(api_client, admin_token, project_id, issue_id,
                              CONTENT_PREFIX + "-真删").json()["data"]
    resp = api_client.delete(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/comments/{created['id']}",
        headers=auth_headers(admin_token))
    assert resp.status_code == 200
    # 无软删除: 再取 404
    resp = api_client.get(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/comments/{created['id']}",
        headers=auth_headers(admin_token))
    assert resp.status_code == 404, "删除后真实不存在"


def test_outsider_cannot_access_comments(api_client, admin_token, member_token,
                                         create_api_user, unique_suffix, cleanup_orgs):
    """user1 未入项目 → 数据级 403（authority 不豁免数据级）。"""
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}/comments",
                          headers=auth_headers(member_token))
    assert resp.status_code == 403, "非项目成员（即使 MEMBER 角色）→ 数据级 403"


def test_cross_project_issue_id_returns_404(api_client, admin_token, unique_suffix, cleanup_orgs):
    """projectA 路径 + projectB 的 issueId → 404 不泄露存在性。"""
    _, project_a, issue_a = _create_issue(api_client, admin_token, "A" + unique_suffix, cleanup_orgs)
    _, project_b, issue_b = _create_issue(api_client, admin_token, "B" + unique_suffix, cleanup_orgs)
    resp = _create_comment(api_client, admin_token, project_a, issue_b, CONTENT_PREFIX)
    assert resp.status_code == 404, "跨项目 issueId 拼接 → 404"


def test_validation_rejects_blank_and_oversized(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    blank = _create_comment(api_client, admin_token, project_id, issue_id, "")
    assert blank.status_code == 422, "空 content → 422"
    oversized = _create_comment(api_client, admin_token, project_id, issue_id, "字" * 10001)
    assert oversized.status_code == 422, "超过 10000 字符 → 422"
    boundary = _create_comment(api_client, admin_token, project_id, issue_id, "字" * 10000)
    assert boundary.status_code == 201, "恰好 10000 字符（上限内）→ 201"


def test_unauthenticated_returns_401(api_client, unique_suffix, cleanup_orgs, admin_token):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}/comments")
    assert resp.status_code == 401
