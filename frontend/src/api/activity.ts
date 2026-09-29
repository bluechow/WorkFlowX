import http from './http'
import type { Result } from '@/types/api'

/** 活动条目（后端 ActivityVO，V18） */
export interface ActivityVO {
  id: number
  projectId: number
  issueId: number | null
  actorId: number
  action: 'CREATE' | 'UPDATE' | 'TRANSITION' | 'ASSIGN' | 'COMMENT' | 'ATTACHMENT' | 'LABEL' | 'LINK' | 'DUE'
  issueNo: number | null
  summary: string
  createdAt: string
}

/** 项目活动（倒序） */
export async function listActivities(projectId: number, limit = 50): Promise<ActivityVO[]> {
  const { data } = await http.get<Result<ActivityVO[]>>(`/projects/${projectId}/activities`, {
    params: { limit },
  })
  return data.data ?? []
}

export const ACTIVITY_ACTION_LABELS: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  TRANSITION: '流转',
  ASSIGN: '分派',
  COMMENT: '评论',
  ATTACHMENT: '附件',
  LABEL: '标签',
  LINK: '关联',
  DUE: '截止',
}

export const ACTIVITY_ACTION_TAG_TYPES: Record<string, string> = {
  CREATE: 'success',
  UPDATE: '',
  TRANSITION: 'warning',
  ASSIGN: 'primary',
  COMMENT: 'info',
  ATTACHMENT: 'info',
  LABEL: 'info',
  LINK: 'info',
  DUE: 'warning',
}
