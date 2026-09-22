"""Issue 边界 + 跨组织隔离 + issue_no 并发（Phase 12; P12-13/21/24 增量缺口）。

现有 test_workflow_api 覆盖矩阵/并发；本文件只补 Python 层缺口：
- Issue 字段边界（empty/max title、invalid enum 422、severity on non-BUG 400）
- 跨组织数据隔离（A 组织成员不能访问 B 组织项目 Issue）
- 同项目并发创建 → issue_no 唯一递增（真实并发，非只验证最终状态）
"""
import concurrent.futures

import pytest

from assertions import assert_error, assert_success
from clients import ApiSession
from conftest import BASE_URL, auth_headers
from factories import Factory

pytestmark = pytest.mark.regression


@pytest.fixture()
def factory(admin_token: str) -> Factory:
    session = ApiSession(BASE_URL, admin_token)
    f = Factory(session)
    yield f
    f.cleanup()
    session.close()


def _session_for(token: str) -> ApiSession:
    return ApiSession(BASE_URL, token)


# ===== 字段边界 =====

def test_issue_title_boundaries(factory: Factory):
    org = factory.create_org()
    project = factory.create_project(org["id"])
    # empty title → 422
    resp = factory.client.post(f"/api/v1/projects/{project['id']}/issues",
                               json={"title": "", "type": "TASK"})
    assert_error(resp, 422)
    # max title（200 字符）→ 成功
    max_title = "标" * 200
    ok = factory.create_issue(project["id"], title=max_title)
    assert ok["title"] == max_title
    # over max（201 字符）→ 422
    resp = factory.client.post(f"/api/v1/projects/{project['id']}/issues",
                               json={"title": "标" * 201, "type": "TASK"})
    assert_error(resp, 422)


def test_issue_invalid_enum_returns_422(factory: Factory):
    org = factory.create_org()
    project = factory.create_project(org["id"])
    resp = factory.client.post(f"/api/v1/projects/{project['id']}/issues",
                               json={"title": "AA 枚举", "type": "NOT_A_TYPE"})
    assert_error(resp, 422)
    resp = factory.client.post(f"/api/v1/projects/{project['id']}/issues",
                               json={"title": "AA 枚举", "type": "TASK", "priority": "CRITICAL"})
    assert_error(resp, 422)


def test_severity_on_non_bug_rejected(factory: Factory):
    org = factory.create_org()
    project = factory.create_project(org["id"])
    resp = factory.client.post(f"/api/v1/projects/{project['id']}/issues",
                               json={"title": "AA 非 BUG 带 severity", "type": "TASK", "severity": "S1"})
    assert_error(resp, 400)


def test_four_issue_types_create_successfully(factory: Factory):
    org = factory.create_org()
    project = factory.create_project(org["id"])
    for issue_type in ("BUG", "TASK", "FEATURE", "IMPROVEMENT"):
        issue = factory.create_issue(project["id"], type=issue_type)
        assert issue["type"] == issue_type


# ===== 跨组织隔离 =====

def test_cross_org_issue_access_denied(factory: Factory, member_token: str):
    """B 会话（user1 未入 A 项目）不能读写 A 组织项目 Issue。"""
    org_a = factory.create_org()
    project_a = factory.create_project(org_a["id"])
    issue_a = factory.create_issue(project_a["id"])
    outsider = _session_for(member_token)
    # 读 403（数据级）；写 403
    resp = outsider.get(f"/api/v1/projects/{project_a['id']}/issues/{issue_a['id']}")
    assert_error(resp, 403)
    resp = outsider.patch(f"/api/v1/projects/{project_a['id']}/issues/{issue_a['id']}/status",
                          json={"fromStatus": "OPEN", "toStatus": "IN_PROGRESS"})
    assert_error(resp, 403)


def test_cross_org_department_visibility(factory: Factory, member_token: str):
    """组织部门列表对非成员可见性：user1 未入 B 组织时写操作 403。"""
    org_b = factory.create_org()
    outsider = _session_for(member_token)
    resp = outsider.post(f"/api/v1/orgs/{org_b['id']}/departments",
                         json={"name": "AA 越权部门", "code": "AAXOVER", "parentId": None})
    assert resp.status_code == 403, f"非成员建部门应 403: {resp.status_code} {resp.text}"


# ===== 并发：issue_no 唯一递增 =====

def test_concurrent_issue_creation_unique_sequential_issue_no(factory: Factory, admin_token: str):
    """8 线程同项目并发创建 → issue_no 全局唯一且为 1..8 的排列（行锁串行化）。"""
    org = factory.create_org()
    project = factory.create_project(org["id"])
    threads = 8
    with concurrent.futures.ThreadPoolExecutor(max_workers=threads) as pool:
        futures = [
            pool.submit(factory.create_issue, project["id"], f"AA 并发-{i}")
            for i in range(threads)
        ]
        created = [f.result()["issueNo"] for f in futures]
    assert sorted(created) == list(range(1, threads + 1)), f"issue_no 应为 1..{threads} 的排列: {sorted(created)}"
