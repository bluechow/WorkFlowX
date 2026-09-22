import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 评论视图对象（后端 CommentVO，P8-03） */
export interface CommentVO {
  id: number
  issueId: number
  authorId: number
  content: string
  createdAt: string
  updatedAt: string
}

/** 评论列表（P8-04）：GET /projects/{projectId}/issues/{issueId}/comments */
export async function listComments(
  projectId: number,
  issueId: number,
  page = 1,
  size = 20,
): Promise<PageVO<CommentVO>> {
  const resp = await http.get<Result<PageVO<CommentVO>>>(
    `/projects/${projectId}/issues/${issueId}/comments`,
    { params: { page, size } },
  )
  return resp.data.data as PageVO<CommentVO>
}

/** 创建评论（author 服务端绑定） */
export async function createComment(
  projectId: number,
  issueId: number,
  content: string,
): Promise<CommentVO> {
  const resp = await http.post<Result<CommentVO>>(
    `/projects/${projectId}/issues/${issueId}/comments`,
    { content },
  )
  return resp.data.data as CommentVO
}

/** 编辑本人评论 */
export async function updateComment(
  projectId: number,
  issueId: number,
  commentId: number,
  content: string,
): Promise<CommentVO> {
  const resp = await http.put<Result<CommentVO>>(
    `/projects/${projectId}/issues/${issueId}/comments/${commentId}`,
    { content },
  )
  return resp.data.data as CommentVO
}

/** 删除本人评论（无软删除） */
export async function deleteComment(
  projectId: number,
  issueId: number,
  commentId: number,
): Promise<void> {
  await http.delete<Result<null>>(
    `/projects/${projectId}/issues/${issueId}/comments/${commentId}`,
  )
}
