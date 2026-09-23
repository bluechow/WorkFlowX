"""性能测试数据准备/清理脚本（Phase 14; P14-08）。

用法:
  python prepare_data.py --scale small|medium|large   # 造数（PERF 命名空间）
  python prepare_data.py --cleanup                    # 清理（逆序级联）

规模:
  small  : 1 组织 1 项目 10 Issue      （Baseline/Light）
  medium : 1 组织 2 项目 100 Issue     （Normal/Stress 查询有意义）
  large  : 谨慎使用——分批生成，默认不启用
"""
from __future__ import annotations

import argparse
import sys
import time
import uuid

import httpx
import pymysql

BASE = "http://localhost:8080"
DB = dict(host="localhost", port=3307, user="root",
          password="workflowx_dev_root", database="workflowx", autocommit=True)


def login_admin() -> str:
    resp = httpx.post(f"{BASE}/api/v1/auth/login",
                      json={"username": "admin", "password": "Admin@123456"}, timeout=15)
    assert resp.status_code == 200, resp.text
    return resp.json()["data"]["accessToken"]


def _headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def prepare(scale: str) -> dict:
    token = login_admin()
    h = _headers(token)
    su = uuid.uuid4().hex[:8].upper()
    plan = {"small": (1, 10), "medium": (2, 100), "large": (2, 300)}[scale]
    n_projects, n_issues = plan

    org = httpx.post(f"{BASE}/api/v1/orgs", headers=h,
                     json={"name": f"PERF 组织 {su}", "code": f"PERFORG{su}", "description": None},
                     timeout=15).json()["data"]
    print(f"org={org['id']}")

    # user1 入组织（member 视角数据准备备用）
    httpx.post(f"{BASE}/api/v1/orgs/{org['id']}/members", headers=h,
               json={"userId": 2, "role": "MEMBER", "departmentId": None}, timeout=15)

    projects = []
    issue_ids = []
    issues_per_project = n_issues // n_projects
    start = time.time()
    for p_idx in range(n_projects):
        key = f"PERFP{su}{p_idx}"
        project = httpx.post(f"{BASE}/api/v1/projects", headers=h,
                             json={"name": f"PERF 项目 {su}-{p_idx}", "key": key,
                                   "orgId": org["id"], "description": None},
                             timeout=15).json()["data"]
        projects.append(project["id"])
        httpx.post(f"{BASE}/api/v1/projects/{project['id']}/members", headers=h,
                   json={"userId": 2, "role": "MEMBER"}, timeout=15)
        # 分派到不同状态制造分布（OPEN/IN_PROGRESS 交替）
        for i in range(issues_per_project):
            status = "IN_PROGRESS" if i % 2 == 0 else None
            issue_resp = httpx.post(f"{BASE}/api/v1/projects/{project['id']}/issues", headers=h,
                                    json={"title": f"PERF Issue {su}-{p_idx}-{i}",
                                          "description": "perf data", "type": "TASK",
                                          "priority": "MEDIUM", "severity": None,
                                          "assigneeId": None}, timeout=15)
            assert issue_resp.status_code == 201, issue_resp.text
            issue_ids.append(issue_resp.json()["data"]["id"])
            if status:
                httpx.patch(f"{BASE}/api/v1/projects/{project['id']}/issues/{issue_ids[-1]}/status",
                            headers=h, json={"fromStatus": "OPEN", "toStatus": status}, timeout=15)
    print(f"projects={projects} issues={len(issue_ids)} in {time.time()-start:.1f}s")

    with pymysql.connect(**DB) as conn, conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM issues i JOIN projects p ON i.project_id=p.id "
                    "WHERE p.key LIKE %s", (f"PERFP{su}%",))
        print("DB issue count:", cur.fetchone()[0])

    # 供 JMeter 使用的运行时数据（issue 列表第一页足够）
    list_resp = httpx.get(f"{BASE}/api/v1/projects/{projects[0]}/issues",
                          headers=h, params={"page": 1, "size": 1}, timeout=15)
    print(f"smoke list: {list_resp.status_code}")
    return {"org_id": org["id"], "project_ids": projects, "issue_count": len(issue_ids)}


def cleanup() -> None:
    token = login_admin()
    h = _headers(token)
    conn = pymysql.connect(**DB)
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM organizations WHERE code LIKE 'PERFORG%'")
        org_ids = [r[0] for r in cur.fetchall()]
    removed = 0
    for oid in org_ids:
        resp = httpx.delete(f"{BASE}/api/v1/orgs/{oid}", headers=h, timeout=30)
        removed += resp.status_code == 200
    print(f"cleanup: {removed}/{len(org_ids)} orgs removed (级联 project/issue/comment)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", choices=["small", "medium", "large"])
    parser.add_argument("--cleanup", action="store_true")
    args = parser.parse_args()
    if args.cleanup:
        cleanup()
    elif args.scale:
        prepare(args.scale)
    else:
        parser.print_help()
        sys.exit(1)
