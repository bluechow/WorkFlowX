import http from './http'
import type { PageVO, Result } from '@/types/api'

/** 附件元数据视图对象（后端 AttachmentVO，P8-06；不含下载地址——下载走鉴权接口） */
export interface AttachmentVO {
  id: number
  issueId: number
  fileName: string
  fileSize: number
  contentType: string
  uploaderId: number
  createdAt: string
}

/** 与后端 AttachmentFilePolicy 白名单一致（ADR-018），仅前端预检 UX；后端才是安全边界 */
export const ALLOWED_EXTENSIONS = [
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'txt', 'doc', 'docx', 'xls', 'xlsx', 'zip',
] as const

/** 与后端 attachment.max-size-bytes 默认值一致（10MB） */
export const MAX_SIZE_BYTES = 10 * 1024 * 1024

/** 附件列表 */
export async function listAttachments(
  projectId: number,
  issueId: number,
  page = 1,
  size = 20,
): Promise<PageVO<AttachmentVO>> {
  const resp = await http.get<Result<PageVO<AttachmentVO>>>(
    `/projects/${projectId}/issues/${issueId}/attachments`,
    { params: { page, size } },
  )
  return resp.data.data
}

/** 上传附件（multipart/form-data，字段 file） */
export async function uploadAttachment(
  projectId: number,
  issueId: number,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<AttachmentVO> {
  const form = new FormData()
  form.append('file', file)
  const resp = await http.post<Result<AttachmentVO>>(
    `/projects/${projectId}/issues/${issueId}/attachments`,
    form,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      },
    },
  )
  return resp.data.data
}

/** 下载附件（后端鉴权后返回文件流；禁止绕过后端直连对象存储） */
export async function downloadAttachment(
  projectId: number,
  issueId: number,
  attachment: AttachmentVO,
): Promise<void> {
  const resp = await http.get<Blob>(
    `/projects/${projectId}/issues/${issueId}/attachments/${attachment.id}/download`,
    { responseType: 'blob' },
  )
  const url = URL.createObjectURL(resp.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = attachment.fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/** 删除本人上传的附件（MinIO 对象与元数据一并删除） */
export async function deleteAttachment(
  projectId: number,
  issueId: number,
  attachmentId: number,
): Promise<void> {
  await http.delete<Result<null>>(
    `/projects/${projectId}/issues/${issueId}/attachments/${attachmentId}`,
  )
}
