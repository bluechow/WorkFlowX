/** 统一响应结构（对齐后端 Result<T> / ADR-005） */
export interface Result<T = unknown> {
  code: number
  message: string
  data?: T
  timestamp: string
  traceId: string
}

/** 统一业务错误对象（由 http.ts 拦截器归一化） */
export interface ApiError {
  code: number
  message: string
  traceId?: string
}

export interface HealthInfo {
  status: string
  service: string
  checkedAt: string
}

/** 登录请求（后端 LoginRequest） */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应（后端 LoginResponse；最终用户资料以 /me 为准） */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  roles: string[]
}

/** 用户状态（对齐后端 UserStatus） */
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

/** 用户视图对象（后端 UserVO；不含任何密码字段） */
export interface UserVO {
  id: number
  username: string
  email: string
  nickname: string | null
  status: UserStatus
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** 统一分页结构（后端 PageVO，docs/api/api-conventions.md §5） */
export interface PageVO<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 权限类型（后端 PermissionType） */
export type PermissionType = 'MENU' | 'API' | 'BUTTON'

/** 角色视图对象（后端 RoleVO，P3-03） */
export interface RoleVO {
  id: number
  code: string
  name: string
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

/** 权限视图对象（后端 PermissionVO，P3-03） */
export interface PermissionVO {
  id: number
  code: string
  name: string
  type: PermissionType
  description: string | null
  system: boolean
  createdAt: string
  updatedAt: string
}

/** 组织成员角色（后端 OrgMemberType，V4） */
export type OrgMemberRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** 组织视图对象（后端 OrganizationVO，P4-02） */
export interface OrganizationVO {
  id: number
  name: string
  code: string
  ownerId: number
  description: string | null
  createdAt: string
  updatedAt: string
}

/** 部门视图对象（后端 DepartmentVO，P4-02） */
export interface DepartmentVO {
  id: number
  orgId: number
  parentId: number | null
  name: string
  code: string
  createdAt: string
  updatedAt: string
}

/** 项目状态（后端 ProjectStatus，V6） */
export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

/** 项目视图对象（后端 ProjectVO，P5-02） */
export interface ProjectVO {
  id: number
  orgId: number
  key: string
  name: string
  description: string | null
  status: ProjectStatus
  ownerId: number
  createdAt: string
  updatedAt: string
}

/** 组织成员视图对象（后端 OrganizationMemberVO，P4-02） */
export interface OrganizationMemberVO {
  orgId: number
  userId: number
  role: OrgMemberRole
  departmentId: number | null
  createdAt: string
}
