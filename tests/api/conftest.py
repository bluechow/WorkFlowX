"""共享 fixture：API 客户端 / 认证 token / 测试用户工厂与数据清理（P2-20）。

环境变量（均有与 .env.example 一致的本地默认值）:
  WORKFLOWX_BASE_URL     后端地址，默认 http://localhost:8080
  WORKFLOWX_DB_HOST/PORT/USER/PASSWORD/NAME  数据库直连（仅测试数据清理）
  WORKFLOWX_REDIS_HOST/PORT                  Redis 直连（仅清理会话/失败计数键）

黑盒原则: 业务断言全部通过真实 HTTP 响应；数据库/Redis 直连仅用于测试数据清理，
不参与任何业务行为断言。
"""
import os
import uuid

import httpx
import pymysql
import pytest
import redis

BASE_URL = os.environ.get("WORKFLOWX_BASE_URL", "http://localhost:8080")
USER_PREFIX = "api_test_"
PASSWORD = "ApiTest@123"

DB_CONFIG = dict(
    host=os.environ.get("WORKFLOWX_DB_HOST", "localhost"),
    port=int(os.environ.get("WORKFLOWX_DB_PORT", "3307")),
    user=os.environ.get("WORKFLOWX_DB_USER", "root"),
    password=os.environ.get("WORKFLOWX_DB_PASSWORD", "workflowx_dev_root"),
    database=os.environ.get("WORKFLOWX_DB_NAME", "workflowx"),
    autocommit=True,
)
REDIS_CONFIG = dict(
    host=os.environ.get("WORKFLOWX_REDIS_HOST", "localhost"),
    port=int(os.environ.get("WORKFLOWX_REDIS_PORT", "6379")),
    decode_responses=True,
)


@pytest.fixture(scope="session")
def base_url() -> str:
    return BASE_URL


@pytest.fixture(scope="session")
def api_client():
    """未认证 HTTP 客户端（真实请求）"""
    with httpx.Client(base_url=BASE_URL, timeout=10) as client:
        yield client


@pytest.fixture(scope="session")
def db():
    """数据库直连（仅测试数据清理）"""
    conn = pymysql.connect(**DB_CONFIG)
    try:
        yield conn
    finally:
        conn.close()


@pytest.fixture(scope="session")
def redis_client():
    """Redis 直连（仅清理会话/失败计数键）"""
    client = redis.Redis(**REDIS_CONFIG)
    try:
        yield client
    finally:
        client.close()


def login(api_client: httpx.Client, username: str, password: str) -> dict:
    """登录并返回 data 载荷（fixture/工厂内部使用）"""
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": username, "password": password})
    assert resp.status_code == 200, f"fixture 登录失败: {resp.status_code} {resp.text}"
    return resp.json()["data"]


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture(scope="session")
def admin_token(api_client):
    """dev seed 管理员 token（ADMIN 角色）"""
    return login(api_client, "admin", "Admin@123456")["accessToken"]


@pytest.fixture(scope="session")
def member_token(api_client):
    """dev seed 普通用户 token（MEMBER 角色）"""
    return login(api_client, "user1", "Member@123456")["accessToken"]


@pytest.fixture(scope="session")
def created_users():
    """本运行创建的测试用户 id 列表（供清理会话键）"""
    return []


@pytest.fixture(scope="session")
def cleanup_orgs(api_client, admin_token):
    """本运行经 API 创建的测试组织 id 列表（owner=admin 可删）"""
    org_ids: list[int] = []

    def _cleanup():
        for oid in org_ids:
            try:
                resp = api_client.delete(f"/api/v1/orgs/{oid}", headers=auth_headers(admin_token))
                if resp.status_code not in (200, 404):
                    # P12-26: 清理失败必须可见（曾经 401 被 except pass 静默吞，导致跨轮累积 180+ 组织）
                    print(f"WARNING: cleanup_orgs 删除组织 {oid} 返回 {resp.status_code}")
            except Exception as exc:
                print(f"WARNING: cleanup_orgs 删除组织 {oid} 异常: {exc}")

    yield org_ids
    _cleanup()


@pytest.fixture(scope="session")
def cleanup_roles(api_client, admin_token):
    """本运行经 API 创建的测试角色 id 列表（会话结束经 API 删除，非系统角色可删）"""
    role_ids: list[int] = []

    def _cleanup():
        for rid in role_ids:
            try:
                api_client.delete(f"/api/v1/roles/{rid}", headers=auth_headers(admin_token))
            except Exception:
                pass  # 已被用例自身删除则忽略

    yield role_ids
    _cleanup()


@pytest.fixture(scope="session")
def cleanup(db, redis_client, created_users):
    """测试数据清理: api_test_ 前缀用户（级联 user_roles）+ 会话/失败计数键"""
    def _cleanup():
        with db.cursor() as cur:
            # Phase 12: 工厂用户（api_test_ 前缀）的通知/审计命名空间清理
            # （通知无 FK、审计记录业务事实——按收件人/命名空间回收，而非全表清空）
            cur.execute(
                "DELETE FROM notifications WHERE recipient_id IN "
                "(SELECT id FROM users WHERE username LIKE %s)", (USER_PREFIX + "%",))
            cur.execute(
                "DELETE FROM audit_logs WHERE user_id IN "
                "(SELECT id FROM users WHERE username LIKE %s)", (USER_PREFIX + "%",))
            cur.execute(
                "DELETE FROM audit_logs WHERE summary LIKE %s", ("AA %",))
            # seed 账号（admin/user1）的全部审计：测试运行期以 seed 身份产生的操作记录，
            # 跨轮无保留价值（组织/Issue 等实体已随清理消失，审计留着只会累积误导）
            cur.execute(
                "DELETE FROM audit_logs WHERE user_id IN "
                "(SELECT id FROM users WHERE username IN ('admin', 'user1'))")
            # 匿名失败审计（LOGIN_FAIL 对不存在用户名 → user_id=NULL）：跨轮无保留价值
            cur.execute("DELETE FROM audit_logs WHERE user_id IS NULL")
            # 跨框架兜底（P13/P15/P17）: 清理所有测试组织
            # （pytest cleanup_orgs 漏登记 / Playwright AA* / JMeter 残留——org 级联删 project/issue/comment）
            cur.execute("DELETE FROM organizations")
            # 非 seed 角色及其绑定兜底（P19：测试创建的角色跨轮残留，影响后续 RBAC 相关验证）
            cur.execute(
                "DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code NOT IN ('ADMIN','MEMBER'))")
            cur.execute(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code NOT IN ('ADMIN','MEMBER'))")
            cur.execute("DELETE FROM roles WHERE code NOT IN ('ADMIN','MEMBER')")
            cur.execute(
                "DELETE FROM user_roles WHERE user_id IN "
                "(SELECT id FROM users WHERE username LIKE %s)", (USER_PREFIX + "%",))
            cur.execute("DELETE FROM users WHERE username LIKE %s", (USER_PREFIX + "%",))
        for uid in created_users:
            redis_client.delete(f"auth:session:{uid}")
        for key in redis_client.scan_iter(match=f"auth:fail:{USER_PREFIX}*"):
            redis_client.delete(key)
        # 兜底（P12-26 稳定性）: 清除全部登录失败计数键——
        # auth:fail TTL 900s，多轮连续跑会累积触发 429（401 变 429 的跨轮污染根因）
        for key in redis_client.scan_iter(match="auth:fail:*"):
            redis_client.delete(key)
    return _cleanup


@pytest.fixture(scope="session", autouse=True)
def cleanup_at_session_end(cleanup):
    yield
    cleanup()


@pytest.fixture
def unique_suffix():
    """函数级唯一后缀: 同一运行内每个测试用例得到不同后缀，避免同批用户名冲突"""
    return uuid.uuid4().hex[:8]


@pytest.fixture
def create_api_user(api_client, admin_token, created_users):
    """测试用户工厂: 经真实 ADMIN API 创建（可选预设状态），返回含 password 的完整信息"""
    def _create(suffix: str | None = None, status: str | None = None) -> dict:
        suffix = suffix or uuid.uuid4().hex[:8]
        username = USER_PREFIX + suffix
        payload = {"username": username, "email": f"{username}@test.local",
                   "password": PASSWORD, "nickname": "api-test"}
        resp = api_client.post("/api/v1/users", json=payload,
                               headers=auth_headers(admin_token))
        assert resp.status_code == 201, f"工厂创建失败: {resp.status_code} {resp.text}"
        data = resp.json()["data"]
        created_users.append(data["id"])
        if status and status != "ACTIVE":
            r = api_client.patch(f"/api/v1/users/{data['id']}/status",
                                 json={"status": status}, headers=auth_headers(admin_token))
            assert r.status_code == 200, f"预设状态失败: {r.status_code} {r.text}"
            data["status"] = status
        return {**data, "password": PASSWORD, "username": username}
    return _create
