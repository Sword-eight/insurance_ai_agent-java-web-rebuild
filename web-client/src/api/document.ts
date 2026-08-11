import type { AxiosProgressEvent } from 'axios'
import { http, unwrap } from './http'
import type { ApiResponse, DocumentData, PageResponse } from '../types/api'

export const DOCUMENT_UPLOAD_TIMEOUT_MS = 305_000

export type UploadProgressHandler = (percentage: number | null) => void

export async function listDocuments(): Promise<PageResponse<DocumentData>> {
  const response = await http.get<ApiResponse<PageResponse<DocumentData>>>('/documents', {
    params: { page: 1, size: 100 },
  })
  return unwrap(response.data)
}

export async function uploadDocument(
  file: File,
  onProgress?: UploadProgressHandler,
): Promise<DocumentData> {
  const form = new FormData()
  form.append('file', file)

  const response = await http.post<ApiResponse<DocumentData>>('/documents', form, {
    timeout: DOCUMENT_UPLOAD_TIMEOUT_MS,
    onUploadProgress: (event: AxiosProgressEvent) => {
      const percentage = event.total && event.total > 0
        ? Math.min(100, Math.round((event.loaded / event.total) * 100))
        : null
      onProgress?.(percentage)
    },
  })
  return unwrap(response.data)
}
