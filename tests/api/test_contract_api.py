"""API Response Contract Smoke Suite（Phase 12; P12-08/22）。

背景: Phase 11 暴露前端对 Result/PageVO 包装层级解析错误且 Vitest mock 掩盖——
本套件从真实 HTTP 响应层锁定 envelope/结构/关键字段，后端 contract 变化时最先失败。

分层: smoke（核心可用性）+ contract（结构契约）双 marker。
"""
import httpx
import pytest

from assertions import assert_envelope, assert_page
from clients import ApiSession
from conftest import BASE_URL, auth_headers
from factories import Factory

pytestmark = [pytest.mark.smoke, pytest.mark.contract]


@pytest.fixture(scope="module")
def admin_session(admin_token: str) -> ApiSession:
    """模块级 admin 会话——复用 conftest 会话级 token（P12-04：禁止重登 seed 用户，
    单会话策略下 relogin 会顶掉 conftest.admin_token 污染后续全部测试文件）。"""
    session = ApiSession(BASE_URL, admin_token)
    yield session
    session.close()


@pytest.fixture(scope="module")
def factory(admin_session: ApiSession) -> Factory:
    f = Factory(admin_session)
    yield f
    f.cleanup()


def test_health_contract(api_client: httpx.Client):
    resp = api_client.get("/api/v1/health")
    assert resp.status_code == 200


def test_login_contract_shape(api_client: httpx.Client, create_api_user):
    """LoginResponse 契约: accessToken/tokenType/expiresIn/userId/username/roles。
    用工厂用户登录（P12-04：重登 seed admin 会覆盖单会话、踢掉 conftest.admin_token 污染后续文件）。"""
    user = create_api_user()
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": user["password"]})
    data = assert_envelope(resp).get("data")
    assert set(["accessToken", "tokenType", "expiresIn", "userId", "username", "roles"]).issubset(data.keys())
    assert data["tokenType"] == "Bearer"
    assert isinstance(data["roles"], list), "roles 应为数组（新建工厂用户可为空数组）"


def test_me_contract(admin_session: ApiSession):
    """UserVO 契约: 无 password/敏感字段。"""
    resp = admin_session.get("/api/v1/auth/me")
    data = assert_envelope(resp).get("data")
    assert set(["id", "username", "email", "status"]).issubset(data.keys())
    blob = str(data).lower()
    for banned in ("password", "bearer", "secret"):
        assert banned not in blob, f"/me 响应含敏感词 {banned}"


def test_org_list_contract(admin_session: ApiSession, factory: Factory):
    factory.create_org()
    resp = admin_session.get("/api/v1/orgs", params={"page": 1, "size": 10})
    body = assert_envelope(resp)
    page = assert_page(body, min_total=1)
    org = page["list"][0]
    assert set(["id", "name", "code", "ownerId", "createdAt"]).issubset(org.keys())


def test_project_list_contract(admin_session: ApiSession, factory: Factory):
    org = factory.create_org()
    factory.create_project(org["id"])
    resp = admin_session.get("/api/v1/projects", params={"page": 1, "size": 10})
    page = assert_page(assert_envelope(resp), min_total=1)
    project = page["list"][0]
    assert set(["id", "key", "name", "status", "orgId", "ownerId"]).issubset(project.keys())


def test_issue_contract_and_page(admin_session: ApiSession, factory: Factory):
    org = factory.create_org()
    project = factory.create_project(org["id"])
    issue = factory.create_issue(project["id"], title="AA contract issue", type="BUG",
                                 priority="HIGH", severity="S1")
    assert set(["id", "projectId", "issueNo", "title", "type", "status", "reporterId"]).issubset(issue.keys())
    assert issue["status"] == "OPEN" and issue["issueNo"] == 1

    resp = admin_session.get(f"/api/v1/projects/{project['id']}/issues", params={"page": 1, "size": 10})
    page = assert_page(assert_envelope(resp), min_total=1)
    assert page["list"][0]["title"] == "AA contract issue"


def test_notification_contract(admin_session: ApiSession, factory: Factory, member_token: str):
    """通知触发后 list/unread-count 的结构契约（收件人=user1，用其独立会话查询）。"""
    org = factory.create_org()
    project = factory.create_project(org["id"])
    # 组织成员前置（ADR-015）→ 项目成员 → 才可分派
    admin_session.post(f"/api/v1/orgs/{org['id']}/members", json={"userId": 2, "role": "MEMBER", "departmentId": None})
    admin_session.post(f"/api/v1/projects/{project['id']}/members", json={"userId": 2, "role": "MEMBER"})
    factory.create_issue(project["id"], title="AA 通知契约", assigneeId=2)

    member_headers = auth_headers(member_token)
    count_resp = admin_session._client.get("/api/v1/notifications/unread-count",
                                           headers={"Authorization": f"Bearer {member_token}"})
    count_data = assert_envelope(count_resp).get("data")
    assert set(["count"]).issubset(count_data.keys()) and count_data["count"] >= 1

    resp = admin_session.get("/api/v1/notifications", params={"read": False, "page": 1, "size": 5},
                             headers=member_headers)
    page = assert_page(assert_envelope(resp), min_total=1)
    item = page["list"][0]
    assert set(["id", "type", "title", "content", "isRead", "createdAt", "projectId"]).issubset(item.keys())


def test_audit_contract(admin_session: ApiSession):
    resp = admin_session.get("/api/v1/audit-logs", params={"page": 1, "size": 5})
    page = assert_page(assert_envelope(resp))
    if page["total"] > 0:
        row = page["list"][0]
        assert set(["id", "module", "action", "httpMethod", "uri", "success", "traceId", "createdAt"]).issubset(row.keys())
        blob = str(row).lower()
        for banned in ("password", "bearer", "secret", "credential"):
            assert banned not in blob, f"审计响应含敏感词 {banned}"


def test_dashboard_contract(admin_session: ApiSession, factory: Factory):
    org = factory.create_org()
    factory.create_project(org["id"])
    resp = admin_session.get("/api/v1/dashboard/overview")
    data = assert_envelope(resp).get("data")
    assert set(["projects", "issues", "createdTrend"]).issubset(data.keys())
    assert set(["total", "active", "archived"]).issubset(data["projects"].keys())
    assert set(["total", "byStatus", "byType", "byPriority", "bySeverity", "bugCount"]).issubset(data["issues"].keys())
    assert len(data["createdTrend"]) == 14
    assert all(set(["date", "created"]).issubset(p.keys()) for p in data["createdTrend"])


def test_permission_envelope_on_403(api_client: httpx.Client, member_token: str):
    """403 的 envelope 契约（与 200 同结构，data=null）。"""
    resp = api_client.get("/api/v1/audit-logs", headers=auth_headers(member_token))
    body = assert_envelope(resp, expected_code=403)
    assert resp.status_code == 403
    assert "data" not in body, "安全层 403 响应不含 data 字段（真实契约）"
