"""Issue Attachment API 黑盒测试（Phase 8, P8-13）：真实 HTTP + 真实 MinIO。

安全基线（ADR-018）: 扩展名白名单（不信任 Content-Type）、10MB 上限（413）、
文件名去路径/控制字符、objectKey 服务端生成。二进制存 MinIO，库内仅元数据。
终态断言: 全部附件删除后 MinIO issues/ 下测试对象 = 0。
"""

import subprocess

import httpx

from conftest import auth_headers

KEY_PREFIX = "P8AT"
ORG_PREFIX = "P8AT_ORG_"
WSL = r"C:\Windows\System32\wsl.exe"


def _create_issue(api_client: httpx.Client, admin_token: str, unique_suffix: str,
                  cleanup_orgs: list) -> tuple[int, int, int]:
    org = api_client.post("/api/v1/orgs", headers=auth_headers(admin_token),
                          json={"name": f"P8AT 组织 {unique_suffix}",
                                "code": ORG_PREFIX + unique_suffix.upper(),
                                "description": None}).json()["data"]
    cleanup_orgs.append(org["id"])
    project = api_client.post("/api/v1/projects", headers=auth_headers(admin_token),
                              json={"name": "P8AT 项目", "key": KEY_PREFIX + unique_suffix.upper(),
                                    "orgId": org["id"], "description": None}).json()["data"]
    issue = api_client.post(f"/api/v1/projects/{project['id']}/issues", headers=auth_headers(admin_token),
                            json={"title": "P8AT 目标", "description": "attachment test",
                                  "type": "TASK", "priority": "MEDIUM",
                                  "severity": None, "assigneeId": None}).json()["data"]
    return org["id"], project["id"], issue["id"]


def _upload(api_client: httpx.Client, token: str, project_id: int, issue_id: int,
            filename: str, content: bytes, content_type: str = "text/plain") -> httpx.Response:
    return api_client.post(f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments",
                           headers=auth_headers(token),
                           files={"file": (filename, content, content_type)})


def _delete_attachment(api_client: httpx.Client, token: str, project_id: int,
                       issue_id: int, attachment_id: int) -> httpx.Response:
    return api_client.delete(f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments/{attachment_id}",
                             headers=auth_headers(token))


def minio_issues_object_count() -> int:
    """统计 MinIO workflowx/issues/ 下对象数（黑盒外的基础设施终态校验，仅用于清理验证）。"""
    cmd = [WSL, "-d", "Ubuntu", "-u", "root", "--", "docker", "exec", "workflowx-minio", "sh", "-c",
           "mc alias set local http://localhost:9000 minioadmin workflowx_dev_minio >/dev/null 2>&1; "
           "mc ls --recursive local/workflowx/issues 2>/dev/null | wc -l"]
    out = subprocess.run(cmd, capture_output=True, text=True, timeout=30).stdout.strip()
    return int(out or "0")


def test_upload_txt_metadata(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    payload = "P8AT attachment payload".encode()
    resp = _upload(api_client, admin_token, project_id, issue_id, "p8at-note.txt", payload)
    assert resp.status_code == 201, resp.text
    data = resp.json()["data"]
    assert data["fileName"] == "p8at-note.txt"
    assert data["fileSize"] == len(payload)
    assert data["uploaderId"] == 1, "uploader 自动绑定操作者"
    # 清理: 显式走删除 API（同步删 MinIO 对象，避免 org 级联删除留下孤儿对象）
    assert _delete_attachment(api_client, admin_token, project_id, issue_id, data["id"]).status_code == 200


def test_upload_png_and_pdf(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    png = _upload(api_client, admin_token, project_id, issue_id, "p8at-img.png",
                  b"\x89PNG\r\n\x1a\nfakepng", "image/png")
    assert png.status_code == 201, png.text
    pdf = _upload(api_client, admin_token, project_id, issue_id, "p8at-doc.pdf",
                  b"%PDF-1.4 fake", "application/pdf")
    assert pdf.status_code == 201, pdf.text
    for item in (png.json()["data"], pdf.json()["data"]):
        assert _delete_attachment(api_client, admin_token, project_id, issue_id, item["id"]).status_code == 200


def test_download_returns_identical_bytes(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    payload = "P8AT binary \u00e9\u00e8 0123".encode()
    created = _upload(api_client, admin_token, project_id, issue_id,
                      "p8at-data.txt", payload).json()["data"]
    resp = api_client.get(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments/{created['id']}/download",
        headers=auth_headers(admin_token))
    assert resp.status_code == 200
    assert resp.content == payload, "下载逐字节一致"
    assert "p8at-data.txt" in resp.headers.get("content-disposition", "")
    assert _delete_attachment(api_client, admin_token, project_id, issue_id, created["id"]).status_code == 200


def test_delete_then_download_404(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    created = _upload(api_client, admin_token, project_id, issue_id,
                      "p8at-gone.txt", b"gone").json()["data"]
    assert _delete_attachment(api_client, admin_token, project_id, issue_id, created["id"]).status_code == 200
    resp = api_client.get(
        f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments/{created['id']}/download",
        headers=auth_headers(admin_token))
    assert resp.status_code == 404, "删除后对象与元数据均不存在"
    # 元数据列表同步消失
    listing = api_client.get(f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments",
                             headers=auth_headers(admin_token)).json()["data"]
    assert listing["total"] == 0


def test_forbidden_extensions_rejected(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    for filename, content in [("p8at-evil.exe", b"MZ"), ("p8at-x.svg", b"<svg/>"), ("p8at-x.js", b"alert")]:
        resp = _upload(api_client, admin_token, project_id, issue_id, filename, content)
        assert resp.status_code == 422, f"{filename} 不在白名单 → 422"


def test_empty_and_oversized_rejected(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    empty = _upload(api_client, admin_token, project_id, issue_id, "p8at-empty.txt", b"")
    assert empty.status_code == 422, "空文件 → 422"
    oversized = _upload(api_client, admin_token, project_id, issue_id, "p8at-big.txt",
                        b"a" * (10 * 1024 * 1024 + 1))
    assert oversized.status_code == 413, "10MB+1 → 413"


def test_path_traversal_filename_sanitized(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = _upload(api_client, admin_token, project_id, issue_id,
                   "../../etc/p8at-passwd.txt", b"safe")
    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["fileName"] == "p8at-passwd.txt", "路径分量被剥离"
    assert _delete_attachment(api_client, admin_token, project_id, issue_id, data["id"]).status_code == 200


def test_outsider_upload_rejected(api_client, admin_token, member_token, unique_suffix, cleanup_orgs):
    """user1 未入项目 → 数据级 403。"""
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = _upload(api_client, member_token, project_id, issue_id, "p8at-x.txt", b"x")
    assert resp.status_code == 403


def test_unauthenticated_upload_rejected(api_client, unique_suffix, cleanup_orgs, admin_token):
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    resp = api_client.post(f"/api/v1/projects/{project_id}/issues/{issue_id}/attachments",
                           files={"file": ("p8at-x.txt", b"x", "text/plain")})
    assert resp.status_code == 401


def test_cross_project_issue_rejected(api_client, admin_token, unique_suffix, cleanup_orgs):
    _, project_a, _ = _create_issue(api_client, admin_token, "A" + unique_suffix, cleanup_orgs)
    _, project_b, issue_b = _create_issue(api_client, admin_token, "B" + unique_suffix, cleanup_orgs)
    resp = _upload(api_client, admin_token, project_a, issue_b, "p8at-x.txt", b"x")
    assert resp.status_code == 404, "跨项目 issueId 拼接 → 404"


def test_minio_cleanup_terminal_state(api_client, admin_token, unique_suffix, cleanup_orgs):
    """终态: 本用例上传+删除全链后，MinIO issues/ 下对象总数回落为 0（无孤儿对象）。"""
    _, project_id, issue_id = _create_issue(api_client, admin_token, unique_suffix, cleanup_orgs)
    created = _upload(api_client, admin_token, project_id, issue_id,
                      "p8at-final.txt", b"final").json()["data"]
    assert _delete_attachment(api_client, admin_token, project_id, issue_id, created["id"]).status_code == 200
    remaining = minio_issues_object_count()
    assert remaining == 0, f"MinIO issues/ 残留对象 {remaining} 个（应为 0）"
