import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 用例类型/优先级/状态（对齐后端 V15 ENUM，ADR-022） */
export type TestCaseType = 'FUNCTIONAL' | 'REGRESSION' | 'SMOKE' | 'SECURITY' | 'PERFORMANCE'
export type TestCasePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type TestCaseStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED'

/** 用例目录视图对象（后端 DirectoryVO） */
export interface DirectoryVO {
  id: number
  projectId: number
  parentId: number | null
  name: string
  createdBy: number
  createdAt: string
}

/** 测试用例视图对象（后端 TestCaseVO） */
export interface TestCaseVO {
  id: number
  projectId: number
  directoryId: number | null
  testcaseNo: number
  title: string
  preconditions: string | null
  steps: string | null
  expected: string | null
  type: TestCaseType
  priority: TestCasePriority
  status: TestCaseStatus
  createdBy: number
  createdAt: string
  updatedAt: string
}

/** 用例目录列表（平铺，树由前端组装） */
export async function listDirectories(projectId: number): Promise<DirectoryVO[]> {
  const resp = await http.get<Result<DirectoryVO[]>>(
    `/projects/${projectId}/testcase-directories`,
  )
  return resp.data.data as DirectoryVO[]
}

/** 创建目录 */
export async function createDirectory(
  projectId: number,
  payload: { name: string; parentId: number | null },
): Promise<DirectoryVO> {
  const resp = await http.post<Result<DirectoryVO>>(
    `/projects/${projectId}/testcase-directories`,
    payload,
  )
  return resp.data.data as DirectoryVO
}

/** 更新目录（重命名/移动） */
export async function updateDirectory(
  projectId: number,
  directoryId: number,
  payload: { name?: string; parentId?: number | null },
): Promise<DirectoryVO> {
  const resp = await http.put<Result<DirectoryVO>>(
    `/projects/${projectId}/testcase-directories/${directoryId}`,
    payload,
  )
  return resp.data.data as DirectoryVO
}

/** 删除目录（子目录级联；用例退回未分类） */
export async function deleteDirectory(projectId: number, directoryId: number): Promise<void> {
  await http.delete<Result<null>>(`/projects/${projectId}/testcase-directories/${directoryId}`)
}

/** 用例分页（directoryId 语义: undefined=全部；0=未分类） */
export async function listTestCases(
  projectId: number,
  params: {
    keyword?: string
    directoryId?: number
    status?: TestCaseStatus
    caseType?: TestCaseType
    priority?: TestCasePriority
    page?: number
    size?: number
  },
): Promise<PageVO<TestCaseVO>> {
  const resp = await http.get<Result<PageVO<TestCaseVO>>>(
    `/projects/${projectId}/testcases`,
    { params },
  )
  return resp.data.data as PageVO<TestCaseVO>
}

/** 创建用例 */
export async function createTestCase(
  projectId: number,
  payload: {
    title: string
    preconditions?: string
    steps?: string
    expected?: string
    caseType: TestCaseType
    priority?: TestCasePriority
    status?: TestCaseStatus
    directoryId?: number | null
  },
): Promise<TestCaseVO> {
  const resp = await http.post<Result<TestCaseVO>>(`/projects/${projectId}/testcases`, payload)
  return resp.data.data as TestCaseVO
}

/** 更新用例（null=不变） */
export async function updateTestCase(
  projectId: number,
  testcaseId: number,
  payload: {
    title?: string
    preconditions?: string
    steps?: string
    expected?: string
    caseType?: TestCaseType
    priority?: TestCasePriority
    status?: TestCaseStatus
    directoryId?: number | null
  },
): Promise<TestCaseVO> {
  const resp = await http.put<Result<TestCaseVO>>(
    `/projects/${projectId}/testcases/${testcaseId}`,
    payload,
  )
  return resp.data.data as TestCaseVO
}

/** 删除用例 */
export async function deleteTestCase(projectId: number, testcaseId: number): Promise<void> {
  await http.delete<Result<null>>(`/projects/${projectId}/testcases/${testcaseId}`)
}
