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
def cleanup(db, redis_client, created_users):
    """测试数据清理: api_test_ 前缀用户（级联 user_roles）+ 会话/失败计数键"""
    def _cleanup():
        with db.cursor() as cur:
            cur.execute(
                "DELETE FROM user_roles WHERE user_id IN "
                "(SELECT id FROM users WHERE username LIKE %s)", (USER_PREFIX + "%",))
            cur.execute("DELETE FROM users WHERE username LIKE %s", (USER_PREFIX + "%",))
        for uid in created_users:
            redis_client.delete(f"auth:session:{uid}")
        for key in redis_client.scan_iter(match=f"auth:fail:{USER_PREFIX}*"):
            redis_client.delete(key)
        # 兜底: 清除测试过程中可能对 seed 账号产生的失败计数（避免跨运行锁定管理员）
        for seed in ("admin", "user1"):
            redis_client.delete(f"auth:fail:{seed}")
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
