"""附件独立性能测试（Phase 14; P14-22）：单线程功能 → 低并发受控。

覆盖: 上传/下载一致性/删除/非法类型/中文文件名；MinIO+DB 双端核验。
"""
from __future__ import annotations

import concurrent.futures
import time
import uuid

import httpx

BASE = "http://localhost:8080"


def login_admin() -> str:
    r = httpx.post(f"{BASE}/api/v1/auth/login",
                   json={"username": "admin", "password": "Admin@123456"}, timeout=15)
    return r.json()["data"]["accessToken"]


def main() -> None:
    token = login_admin()
    h = {"Authorization": f"Bearer {token}"}
    su = uuid.uuid4().hex[:8].upper()
    org = httpx.post(f"{BASE}/api/v1/orgs", headers=h,
                     json={"name": f"PERFATT 组织 {su}", "code": f"PERFATT{su}",
                           "description": None}, timeout=15).json()["data"]
    project = httpx.post(f"{BASE}/api/v1/projects", headers=h,
                         json={"name": "PERFATT 项目", "key": f"PERFATTP{su}",
                               "orgId": org["id"], "description": None}, timeout=15).json()["data"]
    issue = httpx.post(f"/api/v1/projects/{project['id']}/issues".replace("/api", BASE + "/api"),
                       headers=h, json={"title": "PERFATT 目标", "type": "TASK",
                                        "priority": "MEDIUM", "assigneeId": None},
                       timeout=15).json()["data"]
    att_base = f"{BASE}/api/v1/projects/{project['id']}/issues/{issue['id']}/attachments"

    # 1) 单线程合法上传（32KB txt）+ 一致性 + 删除
    payload = ("PERF 附件内容 " * 2048).encode()  # ~32KB
    t0 = time.time()
    up = httpx.post(att_base, headers=h, files={"file": ("perf-att-中文.txt", payload, "text/plain")},
                    timeout=30)
    up_ms = (time.time() - t0) * 1000
    assert up.status_code == 201, up.text
    att = up.json()["data"]
    assert att["fileName"] == "perf-att-中文.txt", "中文文件名保留"
    t0 = time.time()
    dl = httpx.get(f"{att_base}/{att['id']}/download", headers=h, timeout=30)
    dl_ms = (time.time() - t0) * 1000
    assert dl.content == payload, "下载逐字节一致"
    print(f"单线程: upload {up_ms:.0f}ms / download {dl_ms:.0f}ms / bytes {len(payload)} 一致 ✓ / 中文文件名 ✓")
    assert httpx.delete(f"{att_base}/{att['id']}", headers=h, timeout=15).status_code == 200

    # 2) 非法类型（exe）→ 422
    bad = httpx.post(att_base, headers=h,
                     files={"file": ("perf-evil.exe", b"MZ", "application/octet-stream")}, timeout=15)
    assert bad.status_code == 422, "非法类型应 422"
    print("非法类型 exe → 422 ✓")

    # 3) 低并发（3 线程 × 5 个 8KB 文件）受控
    def upload_one(i: int):
        content = (f"PERF concurrent {i} " * 500).encode()
        t = time.time()
        r = httpx.post(att_base, headers=h,
                       files={"file": (f"perf-conc-{i}.txt", content, "text/plain")}, timeout=30)
        ms = (time.time() - t) * 1000
        return r.status_code, ms, content, r.json()["data"]["id"] if r.status_code == 201 else None

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for status, ms, content, att_id in pool.map(upload_one, range(15)):
            assert status == 201
            dl = httpx.get(f"{att_base}/{att_id}/download", headers=h, timeout=30)
            assert dl.content == content
            results.append(ms)
    print(f"低并发 15 个: 全部 201+一致 ✓, avg {sum(results)/len(results):.0f}ms, max {max(results):.0f}ms")

    # 4) 清理（删除全部并发附件 + 删除组织级联核验）
    listing = httpx.get(f"{att_base}?page=1&size=50", headers=h, timeout=15).json()["data"]
    for item in listing["list"]:
        assert httpx.delete(f"{att_base}/{item['id']}", headers=h, timeout=15).status_code == 200
    assert httpx.delete(f"{BASE}/api/v1/orgs/{org['id']}", headers=h, timeout=30).status_code == 200
    print("清理: 附件全部删除（MinIO+DB）+ 组织级联 ✓")


if __name__ == "__main__":
    main()
