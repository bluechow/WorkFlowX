"""完整业务链 API 测试（Phase 12; P12-29）。

单测一条真实端到端业务链（全部经 HTTP API，SQL 仅用于终态核验）：
创建用户 → 登录 → 组织 → 部门 → 项目 → 成员 → Issue → 分派 → Workflow →
Comment → Attachment → Notification → Audit → Dashboard。

标记: regression（全链链路验证，业务=Audit=Dashboard 三方一致）。
"""
import httpx
import pytest

from assertions import assert_envelope, assert_success
from clients import ApiSession
from conftest import BASE_URL, auth_headers, db
from factories import Factory

pytestmark = pytest.mark.regression


@pytest.fixture()
def admin_session(admin_token: str) -> ApiSession:
    # P12-04: 复用 conftest 会话级 token——禁止重登 seed 用户（单会话互踩）
    session = ApiSession(BASE_URL, admin_token)
    yield session
    session.close()


@pytest.fixture()
def factory(admin_session: ApiSession) -> Factory:
    f = Factory(admin_session)
    yield f
    f.cleanup()


def test_full_business_chain(api_client: httpx.Client, admin_session: ApiSession,
                             factory: Factory, db):
    # 1. 创建真实用户（ADMIN API）→ 2. 该用户独立登录
    user = factory.create_user()
    user_session = factory.login_as(user)
    assert user_session.token, "工厂用户应能独立登录（单会话互不影响）"

    # 3-4. 组织 + 部门
    org = factory.create_org()
    dept = factory.create_department(org["id"], name="AA 业务链部门")

    # 5. 角色授权（dashboard:view 给 factory 用户，用于末段 Dashboard 核对）
    role = factory.create_role_with_permissions(["dashboard:view"])

    # 6. 项目（admin 创建于该组织）+ 7. 成员（组织成员前置 ADR-015 → 项目成员）
    project = factory.create_project(org["id"])
    resp = admin_session.post(f"/api/v1/orgs/{org['id']}/members",
                              json={"userId": user["id"], "role": "MEMBER", "departmentId": dept["id"]})
    assert resp.status_code in (200, 201), resp.text
    resp = factory.add_project_member(project["id"], user["id"], "MEMBER")
    assert resp.status_code in (200, 201)

    # 8. Issue 创建（admin 创建并分派给工厂用户 → 触发通知）
    issue = factory.create_issue(project["id"], title="AA 全链 Issue",
                                 type="BUG", priority="HIGH", severity="S1", assigneeId=user["id"])
    # 9. Workflow（admin 流转 OPEN→IN_PROGRESS）
    resp = factory.transition(project["id"], issue["id"], "OPEN", "IN_PROGRESS")
    assert_success(resp)

    # 10. Comment（admin 评论）
    resp = admin_session.post(f"/api/v1/projects/{project['id']}/issues/{issue['id']}/comments",
                              json={"content": "AA 全链评论"})
    assert resp.status_code == 201

    # 11. Attachment（admin 上传 txt → 下载字节一致）
    payload = "AA business chain attachment".encode()
    resp = admin_session.post(f"/api/v1/projects/{project['id']}/issues/{issue['id']}/attachments",
                              files={"file": ("aa-chain.txt", payload, "text/plain")})
    assert resp.status_code == 201, resp.text
    attachment_id = resp.json()["data"]["id"]
    download = admin_session.get(
        f"/api/v1/projects/{project['id']}/issues/{issue['id']}/attachments/{attachment_id}/download")
    assert download.content == payload, "下载逐字节一致"
    # 删除附件（双清理）
    assert admin_session.delete(
        f"/api/v1/projects/{project['id']}/issues/{issue['id']}/attachments/{attachment_id}").status_code == 200

    # 12. Notification（工厂用户应收到 分派/状态/评论 3 类通知）
    notif_resp = user_session.get("/api/v1/notifications", params={"read": False, "page": 1, "size": 10})
    notifications = assert_success(notif_resp)
    types = {n["type"] for n in notifications["list"]}
    assert {"ISSUE_ASSIGNED", "ISSUE_STATUS_CHANGED", "ISSUE_COMMENTED"}.issubset(types), f"实际 types={types}"
    unread = user_session.get("/api/v1/notifications/unread-count").json()["data"]["count"]
    assert unread >= 3

    # 13. Audit（admin 查——TRANSITION 事实与业务一致）
    audit = admin_session.get("/api/v1/audit-logs",
                              params={"module": "ISSUE", "action": "TRANSITION", "size": 5}).json()["data"]
    assert audit["total"] >= 1
    assert any(f"issue:{issue['id']}" == row["target"] for row in audit["list"]), "审计 target 与真实 Issue 一致"

    # 14. Dashboard（admin 全局视角核对数据自洽）
    overview = assert_success(admin_session.get("/api/v1/dashboard/overview"))
    assert overview["issues"]["total"] >= 1
    assert overview["issues"]["total"] == sum(overview["issues"]["byStatus"].values())

    # 15. 工厂用户（dashboard:view 授权后）也可见其成员项目范围
    admin_session.post(f"/api/v1/users/{user['id']}/roles", json={"roleCode": role["code"]})
    user_session.relogin(user["username"], user["password"])  # 角色进 JWT
    user_overview = assert_success(user_session.get("/api/v1/dashboard/overview"))
    assert user_overview["projects"]["total"] >= 1, "成员项目统计可见"

    # ---- 终态核验（SQL 仅读，验证 DB 真实状态与 API 一致）----
    with db.cursor() as cur:
        cur.execute("SELECT status FROM issues WHERE id = %s", (issue["id"],))
        assert cur.fetchone()[0] == "IN_PROGRESS", "DB 状态与 Workflow 结果一致"
        cur.execute("SELECT COUNT(*) FROM notifications WHERE recipient_id = %s", (user["id"],))
        assert cur.fetchone()[0] >= 3, "DB 通知行数与 API 一致"
        cur.execute("SELECT COUNT(*) FROM attachments WHERE id = %s", (attachment_id,))
        assert cur.fetchone()[0] == 0, "附件删除后 DB 无残留"
