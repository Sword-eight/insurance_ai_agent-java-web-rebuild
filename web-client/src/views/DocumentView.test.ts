import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../api/http'
import { authState, clearSession } from '../auth/session'
import DocumentView from './DocumentView.vue'

const mocks = vi.hoisted(() => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('../api/document', () => ({
  listDocuments: mocks.listDocuments,
  uploadDocument: mocks.uploadDocument,
}))
vi.mock('../router', () => ({ default: { push: mocks.push, replace: mocks.replace } }))

const indexedDocument = {
  documentId: '77777777-7777-4777-8777-777777777777',
  originalFilename: '医疗险条款.pdf',
  sizeBytes: 1024,
  indexStatus: 'INDEXED' as const,
  createdAt: '2026-08-11T00:00:00Z',
}

const unknownDocument = {
  ...indexedDocument,
  documentId: '88888888-8888-4888-8888-888888888888',
  originalFilename: '车险条款.pdf',
  indexStatus: 'UNKNOWN' as const,
}

async function chooseFile(wrapper: ReturnType<typeof mount>, file: File): Promise<void> {
  const input = wrapper.get<HTMLInputElement>('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('DocumentView', () => {
  beforeEach(() => {
    clearSession()
    authState.user = {
      userId: '11111111-1111-4111-8111-111111111111',
      username: 'candidate',
      createdAt: '2026-08-10T00:00:00Z',
    }
    mocks.listDocuments.mockResolvedValue({
      items: [indexedDocument, unknownDocument], page: 1, size: 100, total: 2,
    })
    mocks.uploadDocument.mockResolvedValue(indexedDocument)
  })

  it('renders Java document states and keeps UNKNOWN distinct from FAILED', async () => {
    const wrapper = mount(DocumentView)
    await flushPromises()

    expect(wrapper.text()).toContain('医疗险条款.pdf')
    expect(wrapper.text()).toContain('可检索')
    expect(wrapper.text()).toContain('结果待确认')
    expect(wrapper.text()).toContain('请勿自动重新上传')
  })

  it('rejects an oversized file before calling the API', async () => {
    const wrapper = mount(DocumentView)
    await flushPromises()
    const file = new File([new Uint8Array(20 * 1024 * 1024 + 1)], 'large.pdf', {
      type: 'application/pdf',
    })

    await chooseFile(wrapper, file)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('不能超过 20 MiB')
    expect(mocks.uploadDocument).not.toHaveBeenCalled()
  })

  it('reports progress and blocks mechanical duplicate upload', async () => {
    let resolveUpload!: (value: typeof indexedDocument) => void
    mocks.uploadDocument.mockImplementation((_file, onProgress) => new Promise((resolve) => {
      onProgress?.(42)
      resolveUpload = resolve
    }))
    const wrapper = mount(DocumentView)
    await flushPromises()
    await chooseFile(wrapper, new File(['%PDF-1.7'], 'terms.pdf', { type: 'application/pdf' }))

    void wrapper.get('form').trigger('submit')
    void wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.uploadDocument).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('已发送 42%')
    expect(wrapper.get('.upload-button').attributes('disabled')).toBeDefined()

    resolveUpload(indexedDocument)
    await flushPromises()
    expect(wrapper.text()).toContain('当前状态：可检索')
  })

  it('shows timeout as unknown, refreshes status with GET and never resubmits', async () => {
    mocks.uploadDocument.mockRejectedValue(new ApiClientError(
      'AI_SERVICE_TIMEOUT',
      'upstream timeout',
      504,
      'trace-document-unknown',
    ))
    const wrapper = mount(DocumentView)
    await flushPromises()
    await chooseFile(wrapper, new File(['%PDF-1.7'], 'terms.pdf', { type: 'application/pdf' }))

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.uploadDocument).toHaveBeenCalledOnce()
    expect(mocks.listDocuments).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('结果暂时无法确认，请勿自动重发')
    expect(wrapper.text()).toContain('trace-document-unknown')
  })
})
