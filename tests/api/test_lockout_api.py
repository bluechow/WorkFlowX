"""登录失败限制 API 黑盒测试（P2-20 + P2-13）。

规则（ADR-010）: 同一 username 15 分钟窗口 5 次失败 → 锁定；统一计数语义（含不存在用户）；
成功登录清除计数。Redis 键/TTL 通过 redis_client 实查（仅验证性读取，非业务断言通道）。
"""

import httpx

from conftest import PASSWORD, USER_PREFIX, auth_headers

FAIL_KEY_PREFIX = "auth:fail:"


def test_five_failures_trigger_lockout(api_client: httpx.Client, unique_suffix, create_api_user, redis_client):
    user = create_api_user(unique_suffix)
    username = user["username"]

    # 第 1~4 次失败: 401
    for i in range(1, 5):
        resp = api_client.post("/api/v1/auth/login",
                               json={"username": username, "password": "Nope@12345"})
        assert resp.status_code == 401, f"第 {i} 次失败应 401"
        assert resp.json()["message"] == "用户名或密码错误"
        assert redis_client.get(f"{FAIL_KEY_PREFIX}{username}") == str(i), f"第 {i} 次失败计数应为 {i}"

    # 第 5 次失败: 429 锁定
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": username, "password": "Nope@12345"})
    assert resp.status_code == 429
    body = resp.json()
    assert body["code"] == 429
    assert body["message"] == "登录尝试次数过多，请稍后再试"

    # Redis 键与 TTL（锁定窗口重置为 15 分钟）
    assert redis_client.get(f"{FAIL_KEY_PREFIX}{username}") == "5"
    ttl = redis_client.ttl(f"{FAIL_KEY_PREFIX}{username}")
    assert 880 <= ttl <= 900, f"锁定 TTL 应接近 900s，实际: {ttl}"

    # 锁定期间正确密码同样 429，且不创建会话
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": username, "password": PASSWORD})
    assert resp.status_code == 429
    assert redis_client.get(f"auth:session:{user['id']}") is None


def test_successful_login_clears_failure_count(api_client: httpx.Client, unique_suffix,
                                               create_api_user, redis_client):
    user = create_api_user(unique_suffix)
    username = user["username"]
    for _ in range(3):
        api_client.post("/api/v1/auth/login",
                        json={"username": username, "password": "Nope@12345"})
    assert redis_client.get(f"{FAIL_KEY_PREFIX}{username}") == "3"

    # 成功登录清除计数
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": username, "password": PASSWORD})
    assert resp.status_code == 200
    assert redis_client.get(f"{FAIL_KEY_PREFIX}{username}") is None

    # 重新失败从 1 开始（而非继续累计）
    resp = api_client.post("/api/v1/auth/login",
                           json={"username": username, "password": "Nope@12345"})
    assert resp.status_code == 401
    assert redis_client.get(f"{FAIL_KEY_PREFIX}{username}") == "1"


def test_lockout_message_does_not_reveal_user_existence(api_client: httpx.Client, unique_suffix,
                                                        create_api_user, redis_client, cleanup):
    # 真实用户与不存在用户名锁定后的 429 响应必须一致（防枚举）
    real = create_api_user(unique_suffix)
    ghost = USER_PREFIX + "ghost_" + unique_suffix
    try:
        for _ in range(5):
            api_client.post("/api/v1/auth/login",
                            json={"username": real["username"], "password": "Nope@12345"})
        for _ in range(5):
            api_client.post("/api/v1/auth/login",
                            json={"username": ghost, "password": "Nope@12345"})

        real_resp = api_client.post("/api/v1/auth/login",
                                    json={"username": real["username"], "password": "Nope@12345"})
        ghost_resp = api_client.post("/api/v1/auth/login",
                                     json={"username": ghost, "password": "Nope@12345"})
        assert real_resp.status_code == ghost_resp.status_code == 429
        for resp in (real_resp, ghost_resp):
            body = dict(resp.json())
            body.pop("timestamp", None)
            body.pop("traceId", None)
            assert body == {"code": 429, "message": "登录尝试次数过多，请稍后再试"}
    finally:
        redis_client.delete(f"{FAIL_KEY_PREFIX}{real['username']}", f"{FAIL_KEY_PREFIX}{ghost}")
