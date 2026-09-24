"""认证安全测试（Phase 15; P15-01）。marker: security。

SECURITY 命名空间约定：不存在的探测用户名用 SECURITY_ 前缀（无需真实存在）；
需要真实登录的动态用户复用 api_test_ 工厂（进 conftest 清理通道）。

覆盖: 防用户枚举（统一 401 契约）/malformed JSON/缺字段/null/空串/超长/mass assignment
探测/失败计数阶梯 1~5 次/锁定后正确密码仍拒绝/成功登录清除计数。
不进行大量爆破（每用户 ≤5 次失败，符合 Phase 15 安全边界）。
"""
import pytest

pytestmark = pytest.mark.security


def test_login_success_returns_token(api_client, create_api_user):
    user = create_api_user()
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": user["password"]})
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["accessToken"] and data["tokenType"] == "Bearer"
    assert data["userId"] == user["id"]


def test_login_failures_do_not_leak_user_existence(api_client, unique_suffix, create_api_user):
    """存在用户+错密 与 不存在用户 → 完全一致的 401 契约（防枚举，P15-01 核心）。"""
    user = create_api_user()
    r_exist = api_client.post("/api/v1/auth/login",
                              json={"username": user["username"], "password": "WrongPass@1"})
    r_missing = api_client.post("/api/v1/auth/login",
                                json={"username": f"SECURITY_no_such_{unique_suffix}",
                                      "password": "Whatever@1"})
    assert r_exist.status_code == 401 and r_missing.status_code == 401
    b1, b2 = r_exist.json(), r_missing.json()
    assert b1["code"] == b2["code"], "业务 code 必须一致（防枚举）"
    assert b1["message"] == b2["message"], "message 必须一致（防枚举）"
    blob = (b1["message"] + b2["message"]).lower()
    for banned in ("not exist", "不存在", "does not exist"):
        assert banned not in blob, f"泄露用户存在性: {banned}"


@pytest.mark.parametrize("payload", [
    "{not-json",
    {},
    {"username": None, "password": None},
    {"username": "", "password": ""},
    {"username": "SECURITY_x", "password": "p" * 200},
], ids=["malformed-json", "missing-fields", "null-fields", "empty-strings", "overlong-password"])
def test_login_malformed_inputs_rejected(api_client, payload):
    resp = api_client.post("/api/v1/auth/login", content=payload,
                           headers={"Content-Type": "application/json"}) \
        if isinstance(payload, str) else \
        api_client.post("/api/v1/auth/login", json=payload)
    assert resp.status_code in (400, 401, 422), f"{payload} → {resp.status_code}"
    assert resp.json().get("traceId"), "错误响应含 traceId"


def test_login_extra_fields_do_not_grant_privileges(api_client, create_api_user):
    """mass assignment 探测：多余字段（roles/admin/userId）不影响登录与授权。"""
    user = create_api_user()
    resp = api_client.post("/api/v1/auth/login", json={
        "username": user["username"], "password": user["password"],
        "roles": ["ADMIN"], "userId": 1, "admin": True,
    })
    assert resp.status_code == 200
    assert "ADMIN" not in resp.json()["data"]["roles"], "多余字段不得注入角色"


def test_failure_count_ladder_and_lockout(api_client, create_api_user):
    """失败 1~4 次=401、第 5 次=429 锁定、锁定后正确密码仍拒绝（ADR-010）。"""
    user = create_api_user()
    for attempt in range(1, 5):
        resp = api_client.post("/api/v1/auth/login",
                               json={"username": user["username"], "password": "Wrong@1"})
        assert resp.status_code == 401, f"第{attempt}次失败应 401，实际 {resp.status_code}"
    resp5 = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": "Wrong@1"})
    assert resp5.status_code == 429, "第 5 次失败触发锁定"
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": user["password"]})
    assert resp.status_code == 429, "锁定期间正确密码仍拒绝"


def test_successful_login_clears_failure_count(api_client, create_api_user):
    user = create_api_user()
    api_client.post("/api/v1/auth/login",
                    json={"username": user["username"], "password": "Wrong@1"})
    assert api_client.post("/api/v1/auth/login",
                           json={"username": user["username"],
                                 "password": user["password"]}).status_code == 200
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": user["username"], "password": "Wrong@2"})
    assert resp.status_code == 401, "成功登录后失败计数应已清零（重新从 401 计）"
