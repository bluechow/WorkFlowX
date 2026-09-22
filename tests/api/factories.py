"""统一测试数据 Factory（Phase 12; P12-05/06）。

设计:
- 命名空间: 用户沿用 conftest 的 api_test_ 前缀（进现有清理通道）；组织/项目/角色用 AA 前缀
- 每个工厂: 真实 API 创建（禁止直插 DB）+ 追踪 id + factory.cleanup() 级联回收
- 唯一数据: uuid 短后缀；不依赖固定 ID / 残留数据
- 单会话安全: 工厂用户均为动态创建账号，重登只影响自己（绝不重登 seed 用户）
"""
from __future__ import annotations

import uuid
from typing import Any

from clients import ApiSession


def short_suffix() -> str:
    return uuid.uuid4().hex[:8].upper()


class Factory:
    """业务数据工厂基座：持有 admin 会话与创建轨迹，cleanup 逆序回收。"""

    def __init__(self, client: ApiSession, db=None):
        self.client = client
        self.db = db
        self._orgs: list[int] = []
        self._projects: list[int] = []
        self._issues: list[tuple[int, int]] = []  # (project_id, issue_id)
        self._departments: list[tuple[int, int]] = []  # (org_id, dept_id)
        self._roles: list[int] = []
        self._users: list[dict] = []

    # ---- User ----
    def create_user(self, suffix: str | None = None, status: str | None = None) -> dict:
        """经 ADMIN API 创建用户；返回含 id/username/password 的完整信息。"""
        suffix = suffix or uuid.uuid4().hex[:8]
        username = f"api_test_{suffix}"
        payload = {"username": username, "email": f"{username}@test.local",
                   "password": "ApiTest@123", "nickname": "aa-factory"}
        resp = self.client.post("/api/v1/users", json=payload)
        assert resp.status_code == 201, f"factory create_user 失败: {resp.status_code} {resp.text}"
        data = resp.json()["data"]
        record = {**data, "username": username, "password": "ApiTest@123"}
        self._users.append(record)
        if status and status != "ACTIVE":
            self.client.patch(f"/api/v1/users/{data['id']}/status", json={"status": status})
            record["status"] = status
        return record

    def login_as(self, user: dict) -> ApiSession:
        """为工厂用户开独立会话（不触碰 seed 用户会话）。"""
        session = ApiSession(str(self.client._client.base_url))
        session.relogin(user["username"], user["password"])
        return session

    # ---- Organization / Department ----
    def create_org(self, suffix: str | None = None, owner_id: int = 1) -> dict:
        suffix = suffix or short_suffix()
        resp = self.client.post("/api/v1/orgs", json={
            "name": f"AA 组织 {suffix}", "code": f"AAORG{suffix}", "description": None})
        assert resp.status_code in (200, 201), f"factory create_org 失败: {resp.status_code} {resp.text} | url=/api/v1/orgs"
        org = resp.json()["data"]
        self._orgs.append(org["id"])
        return org

    def create_department(self, org_id: int, name: str | None = None,
                          parent_id: int | None = None) -> dict:
        suffix = short_suffix()
        resp = self.client.post(f"/api/v1/orgs/{org_id}/departments", json={
            "name": name or f"AA 部门 {suffix}", "code": f"AAD{suffix}", "parentId": parent_id})
        assert resp.status_code in (200, 201), f"factory create_department 失败: {resp.status_code} {resp.text}"
        dept = resp.json()["data"]
        self._departments.append((org_id, dept["id"]))
        return dept

    # ---- Project / Member ----
    def create_project(self, org_id: int, suffix: str | None = None) -> dict:
        suffix = suffix or short_suffix()
        resp = self.client.post("/api/v1/projects", json={
            "name": f"AA 项目 {suffix}", "key": f"AAP{suffix}", "orgId": org_id, "description": None})
        assert resp.status_code in (200, 201), f"factory create_project 失败: {resp.status_code} {resp.text}"
        project = resp.json()["data"]
        self._projects.append(project["id"])
        return project

    def add_project_member(self, project_id: int, user_id: int, role: str = "MEMBER") -> httpx.Response:
        return self.client.post(f"/api/v1/projects/{project_id}/members",
                                json={"userId": user_id, "role": role})

    # ---- Issue ----
    def create_issue(self, project_id: int, title: str | None = None, **overrides: Any) -> dict:
        payload: dict[str, Any] = {
            "title": title or f"AA Issue {short_suffix()}", "description": "aa-factory",
            "type": "TASK", "priority": "MEDIUM", "severity": None, "assigneeId": None,
        }
        payload.update(overrides)
        resp = self.client.post(f"/api/v1/projects/{project_id}/issues", json=payload)
        assert resp.status_code in (200, 201), f"factory create_issue 失败: {resp.status_code} {resp.text}"
        issue = resp.json()["data"]
        self._issues.append((project_id, issue["id"]))
        return issue

    def transition(self, project_id: int, issue_id: int, from_status: str, to_status: str):
        return self.client.patch(f"/api/v1/projects/{project_id}/issues/{issue_id}/status",
                                 json={"fromStatus": from_status, "toStatus": to_status})

    # ---- Role ----
    def create_role_with_permissions(self, permission_codes: list[str],
                                     suffix: str | None = None) -> dict:
        suffix = suffix or short_suffix()
        resp = self.client.post("/api/v1/roles", json={
            "code": f"AAR{suffix}", "name": f"aa-role-{suffix}", "description": None})
        assert resp.status_code in (200, 201), f"factory create_role 失败: {resp.status_code} {resp.text}"
        role = resp.json()["data"]
        self._roles.append(role["id"])
        self.client.put(f"/api/v1/roles/{role['id']}/permissions",
                        json={"permissionCodes": permission_codes})
        return role

    # ---- Cleanup（逆序：角色绑定→角色→项目→组织；Issue/部门随 FK 级联）----
    def cleanup(self) -> None:
        for role_id in reversed(self._roles):
            try:
                self.client.delete(f"/api/v1/roles/{role_id}")
            except Exception:
                pass
        for project_id in reversed(self._projects):
            try:
                # 项目无物理删除——随组织级联（org 删除级联 project/issue/comment）
                pass
            except Exception:
                pass
        for org_id in reversed(self._orgs):
            try:
                self.client.delete(f"/api/v1/orgs/{org_id}")
            except Exception:
                pass
        self._orgs.clear()
        self._projects.clear()
        self._issues.clear()
        self._departments.clear()
        self._roles.clear()
        self._users.clear()
