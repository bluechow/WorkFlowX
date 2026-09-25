import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 计划状态（对齐后端 V16 ENUM，ADR-023） */
export type TestPlanStatus = 'NOT_STARTED' | 'RUNNING' | 'COMPLETED'

/** 执行结果（对齐后端 V16 ENUM） */
export type ItemResult = 'PENDING' | 'PASS' | 'FAIL' | 'BLOCKED'

/** 测试计划视图对象（后端 TestPlanVO，含实时统计） */
export interface TestPlanVO {
  id: number
  projectId: number
  name: string
  status: TestPlanStatus
  createdBy: number
  createdAt: string
  total: number
  passed: number
  failed: number
  blocked: number
  pending: number
}

/** 计划条目视图对象（后端 TestPlanItemVO，含用例摘要） */
export interface TestPlanItemVO {
  id: number
  planId: number
  caseId: number
  testcaseNo: number | null
  caseTitle: string | null
  caseType: string | null
  casePriority: string | null
  caseStatus: string | null
  result: ItemResult
  executedBy: number | null
  executedAt: string | null
  note: string | null
  issueId: number | null
}

/** 计划分页 */
export async function listTestPlans(
  projectId: number,
  page = 1,
  size = 20,
): Promise<PageVO<TestPlanVO>> {
  const resp = await http.get<Result<PageVO<TestPlanVO>>>(
    `/projects/${projectId}/testplans`,
    { params: { page, size } },
  )
  return resp.data.data as PageVO<TestPlanVO>
}

/** 计划详情（含统计） */
export async function getTestPlan(projectId: number, planId: number): Promise<TestPlanVO> {
  const resp = await http.get<Result<TestPlanVO>>(`/projects/${projectId}/testplans/${planId}`)
  return resp.data.data as TestPlanVO
}

/** 计划条目列表 */
export async function listPlanItems(
  projectId: number,
  planId: number,
): Promise<TestPlanItemVO[]> {
  const resp = await http.get<Result<TestPlanItemVO[]>>(
    `/projects/${projectId}/testplans/${planId}/items`,
  )
  return resp.data.data as TestPlanItemVO[]
}

/** 创建计划 */
export async function createTestPlan(projectId: number, name: string): Promise<TestPlanVO> {
  const resp = await http.post<Result<TestPlanVO>>(`/projects/${projectId}/testplans`, { name })
  return resp.data.data as TestPlanVO
}

/** 更新计划（名称/状态） */
export async function updateTestPlan(
  projectId: number,
  planId: number,
  payload: { name?: string; status?: TestPlanStatus },
): Promise<TestPlanVO> {
  const resp = await http.put<Result<TestPlanVO>>(
    `/projects/${projectId}/testplans/${planId}`,
    payload,
  )
  return resp.data.data as TestPlanVO
}

/** 删除计划 */
export async function deleteTestPlan(projectId: number, planId: number): Promise<void> {
  await http.delete<Result<null>>(`/projects/${projectId}/testplans/${planId}`)
}

/** 向计划添加用例（返回实际新增数） */
export async function addPlanItems(
  projectId: number,
  planId: number,
  caseIds: number[],
): Promise<number> {
  const resp = await http.post<Result<number>>(`/projects/${projectId}/testplans/${planId}/items`, {
    caseIds,
  })
  return (resp.data.data as number) ?? 0
}

/** 移除条目 */
export async function removePlanItem(
  projectId: number,
  planId: number,
  itemId: number,
): Promise<void> {
  await http.delete<Result<null>>(`/projects/${projectId}/testplans/${planId}/items/${itemId}`)
}

/** 执行打结果（FAIL/BLOCKED 可关联 Issue） */
export async function executeItem(
  projectId: number,
  planId: number,
  itemId: number,
  payload: { result: ItemResult; note?: string; issueId?: number | null },
): Promise<TestPlanItemVO> {
  const resp = await http.put<Result<TestPlanItemVO>>(
    `/projects/${projectId}/testplans/${planId}/items/${itemId}/execute`,
    payload,
  )
  return resp.data.data as TestPlanItemVO
}
