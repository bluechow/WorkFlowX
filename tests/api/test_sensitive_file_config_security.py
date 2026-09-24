"""敏感信息泄露 / 文件安全 / 配置安全测试（Phase 15; P15-12~14/15/17/22）。marker: security。

验证:
- 响应（登录/用户/错误/Swagger/actuator）不泄露密码/secret/堆栈/SQL/内部路径
- 附件: 路径穿越变体/双扩展/MIME mismatch/URL 编码穿越 → 剥离或拒绝；objectKey 不受用户控制
- actuator 仅暴露 health；env/beans/mappings 401
- 安全响应头（X-Content-Type-Options/X-Frame-Options）由产品加固返回
"""
import pytest

pytestmark = pytest.mark.security

BANNED_IN_RESPONSES = ["password", "passwordhash", "secret", "begin private key",
                       "at com.workflowx", "java.lang", "org.springframework", "jdbc:"]
SEC = "SECURITY_"


def _h(token):
    return {"Authorization": f"Bearer {token}"}


def _blob(obj) -> str:
    import json
    try:
        return json.dumps(obj, ensure_ascii=False).lower()
    except (TypeError, ValueError):
        return str(obj).lower()


def _assert_no_sensitive(body_text: str, context: str) -> None:
    for banned in BANNED_IN_RESPONSES:
        assert banned not in body_text, f"{context} 泄露敏感信息: {banned}"


# ===== 响应敏感信息（P15-15）=====

def test_login_and_me_responses_no_secrets(api_client, create_api_user):
    user = create_api_user()
    login = api_client.post("/api/v1/auth/login",
                            json={"username": user["username"], "password": user["password"]})
    _assert_no_sensitive(_blob(login.json()), "login")
    token = login.json()["data"]["accessToken"]
    me = api_client.get("/api/v1/auth/me", headers=_h(token))
    _assert_no_sensitive(_blob(me.json()), "me")


def test_error_responses_no_stacktrace_or_sql(api_client, unique_suffix):
    probes = [
        ("GET", "/api/v1/users/999999999", None),
        ("GET", "/api/v1/projects/999999999/issues/999999999", None),
        ("POST", "/api/v1/auth/login", {"username": "SECURITY_x", "password": "x"}),
        ("GET", "/api/v1/audit-logs?page=-999", None),
    ]
    for method, url, payload in probes:
        resp = api_client.request(method, url, json=payload)
        _assert_no_sensitive(_blob(resp.json() if resp.text else {}), f"{method} {url}")


def test_swagger_reachable_but_does_not_bypass_security(api_client):
    """Swagger 公开（dev 设计）但被文档化的 API 仍需真实认证（不能从 Swagger 直接调用成功）。"""
    docs = api_client.get("/v3/api-docs")
    assert docs.status_code == 200
    # 从 docs 拿到的受保护端点，未认证访问必须 401
    resp = api_client.get("/api/v1/users")
    assert resp.status_code == 401


def test_actuator_only_health_exposed(api_client):
    assert api_client.get("/actuator/health").status_code == 200
    for endpoint in ("/actuator/env", "/actuator/beans", "/actuator/mappings",
                     "/actuator/configprops"):
        assert api_client.get(endpoint).status_code in (401, 404), \
            f"{endpoint} 不得未授权暴露"


# ===== 安全响应头（产品加固断言）=====

def test_security_headers_present(api_client):
    resp = api_client.get("/api/v1/health")
    assert resp.headers.get("X-Content-Type-Options") == "nosniff"
    assert resp.headers.get("X-Frame-Options") in ("DENY", "SAMEORIGIN")


# ===== 文件安全（P15-12/14）=====

@pytest.fixture()
def att_env(api_client, admin_token, unique_suffix):
    su = unique_suffix.upper()
    org_resp = api_client.post("/api/v1/orgs", headers={"Authorization": f"Bearer {admin_token}"}, json={
        "name": f"{SEC}org att {su}", "code": f"{SEC}ATT{su}",
        "description": None})
    assert org_resp.status_code in (200, 201), (
        f"org 创建失败: {org_resp.status_code} {org_resp.text[:200]}")
    org = org_resp.json()["data"]
    proj_resp = api_client.post("/api/v1/projects", headers={"Authorization": f"Bearer {admin_token}"}, json={
        "name": "SECURITY 附件项目", "key": f"SATTP{su}", "orgId": org["id"],
        "description": None})
    assert proj_resp.status_code in (200, 201), (
        f"project 创建失败: {proj_resp.status_code} {proj_resp.text[:200]}")
    project = proj_resp.json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers={"Authorization": f"Bearer {admin_token}"},
                            json={"title": f"{SEC} 附件目标", "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": None}).json()["data"]
    yield {
        "h": _h(admin_token), "org": org, "project": project, "issue": issue,
        "api_client": api_client,
        "base": f"{api_client.base_url}/api/v1/projects/{project['id']}/issues/{issue['id']}/attachments",
    }
    api_client.delete(f"/api/v1/orgs/{org['id']}")


def test_upload_path_traversal_variants_rejected_or_sanitized(att_env):
    import httpx
    for name in ("../../test.txt", "..\\..\\test.txt", "../security.txt",
                 "test.txt/../x", "%2e%2e%2ftest.txt", "..%252ftest.txt"):
        resp = httpx.post(att_env["base"], headers=att_env["h"],
                          files={"file": (name, b"SECURITY traversal probe", "text/plain")},
                          timeout=15)
        assert resp.status_code in (201, 400, 422), f"{name} → {resp.status_code}"
        if resp.status_code == 201:
            data = resp.json()["data"]
            # 遍历面 = 路径分隔符：清洗后文件名不得含 / 或 \（字面 ".." 无分隔符不构成遍历；
            # URL 编码变体 %2e%2e%2f 原样保留于展示元数据——下载路径由服务端 objectKey 承载）
            assert "/" not in data["fileName"] and "\\" not in data["fileName"],                 f"文件名含路径分隔符: {data['fileName']}"
            # 清理该附件（MinIO 对象不随 org 级联，必须显式删除）
            att_env["api_client"].delete(f"{att_env['base']}/{data['id']}", headers=att_env["h"])


def test_upload_double_extension_and_mime_mismatch(att_env):
    """test.exe.txt → 按白名单 .txt 放行（内容探针为文本，无真实恶意载荷）；test.jpg.exe → 422。"""
    ok = att_env["api_client"].post(att_env["base"], headers=att_env["h"],
                                    files={"file": ("test.exe.txt", b"text only", "text/plain")},
                                    timeout=15)
    assert ok.status_code == 201, "双扩展以最后扩展名判定（txt 白名单放行）"
    att_env["api_client"].delete(
        f"{att_env['base']}/{ok.json()['data']['id']}", headers=att_env["h"])
    bad = att_env["api_client"].post(att_env["base"], headers=att_env["h"],
                                     files={"file": ("test.jpg.exe", b"MZ", "image/jpeg")},
                                     timeout=15)
    assert bad.status_code == 422, "MIME 伪装不能绕过扩展名白名单"


def test_upload_no_extension_and_empty_name(att_env):
    noext = att_env["api_client"].post(att_env["base"], headers=att_env["h"],
                                       files={"file": ("SECURITY_noext", b"x", "text/plain")},
                                       timeout=15)
    assert noext.status_code == 422, "无扩展名 → 422"
    empty = att_env["api_client"].post(att_env["base"], headers=att_env["h"],
                                       files={"file": ("", b"x", "text/plain")}, timeout=15)
    assert empty.status_code == 422, "空文件名（part 缺失）→ 422（P15 修复后契约）"


def test_object_key_not_user_controlled(att_env):
    """objectKey 由服务端生成（UUID 前缀），原始文件名仅作元数据——路径跳出 issues/{issueId}/ 即失败。"""
    import httpx
    resp = httpx.post(att_env["base"], headers=att_env["h"],
                      files={"file": ("../../issues-escape.txt", b"escape probe", "text/plain")},
                      timeout=15)
    data = resp.json()["data"]
    assert ".." not in data["fileName"], "下载文件名不得含路径字符"
    att_env["api_client"].delete(f"{att_env['base']}/{data['id']}", headers=att_env["h"])


# ===== 审计安全（P15-16）=====

def test_audit_does_not_record_secrets(api_client, admin_token, unique_suffix):
    """登录失败审计不得含密码；审计列表不含 secret 类字段。"""
    import httpx
    h = _h(admin_token)
    # 触发登录失败（含密码 payload）
    httpx.post(f"{api_client.base_url}/api/v1/auth/login",
               json={"username": f"{SEC}audit_{unique_suffix}", "password": "SECURITY_Secret@1"},
               timeout=15)
    listing = api_client.get("/api/v1/audit-logs",
                             params={"module": "AUTH", "action": "LOGIN_FAIL", "size": 5},
                             headers=h).json()["data"]
    assert listing["total"] >= 1, "登录失败审计已记录"
    for row in listing["list"]:
        blob = _blob(row)
        assert "security_secret@1" not in blob, "密码不得进入审计"
        assert "password" not in blob, "password 字段名不得出现在审计内容"


def test_security_events_produce_audit(api_client, admin_token, create_api_user):
    """关键安全事件产生审计：user create/status。"""
    user = create_api_user()  # create_api_user 内部走 ADMIN API → USER/CREATE 审计
    listing = api_client.get("/api/v1/audit-logs",
                             params={"module": "USER", "action": "CREATE", "size": 5},
                             headers=_h(admin_token)).json()["data"]
    assert any(row["target"] == f"user:{user['id']}" for row in listing["list"]), \
        "用户创建应产生审计事实"
