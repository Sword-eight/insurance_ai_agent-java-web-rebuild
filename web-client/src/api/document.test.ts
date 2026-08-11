import type { AxiosProgressEvent } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { DOCUMENT_UPLOAD_TIMEOUT_MS, listDocuments, uploadDocument } from './document'
import { http } from './http'

const document = {
  documentId: '77777777-7777-4777-8777-777777777777',
  originalFilename: 'terms.pdf',
  sizeBytes: 12,
  indexStatus: 'INDEXED' as const,
  createdAt: '2026-08-11T00:00:00Z',
}

function envelope<T>(data: T) {
  return {
    data: {
      code: 'OK',
      message: 'success',
      data,
      traceId: 'trace-document',
      timestamp: '2026-08-11T00:00:00Z',
    },
  }
}

describe('document API', () => {
  it('loads documents only from the Java public endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(envelope({
      items: [document], page: 1, size: 100, total: 1,
    }))

    await expect(listDocuments()).resolves.toMatchObject({ items: [document] })
    expect(get).toHaveBeenCalledWith('/documents', { params: { page: 1, size: 100 } })
  })

  it('uploads the PDF as FormData with a knowledge-specific timeout and real progress', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(envelope(document))
    const file = new File(['%PDF-1.7'], 'terms.pdf', { type: 'application/pdf' })
    const progress: Array<number | null> = []

    await expect(uploadDocument(file, (value) => progress.push(value))).resolves.toEqual(document)

    expect(post).toHaveBeenCalledWith('/documents', expect.any(FormData), expect.objectContaining({
      timeout: DOCUMENT_UPLOAD_TIMEOUT_MS,
      onUploadProgress: expect.any(Function),
    }))
    const body = post.mock.calls[0]?.[1] as FormData
    const config = post.mock.calls[0]?.[2]
    expect(body.get('file')).toBe(file)

    config?.onUploadProgress?.({ loaded: 5, total: 10 } as AxiosProgressEvent)
    config?.onUploadProgress?.({ loaded: 5 } as AxiosProgressEvent)
    expect(progress).toEqual([50, null])
  })
})
