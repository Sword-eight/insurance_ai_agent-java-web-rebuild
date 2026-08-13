import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiResponse, LoginView } from '../types/api'
import { getAccessToken, saveSession } from '../auth/session'
import {
  ApiClientError,
  formatApiError,
  handleResponseError,
  setUnauthorizedHandler,
  unwrap,
} from './http'

function unauthorizedError(): AxiosError<ApiResponse<unknown>> {
  const error = new AxiosError<ApiResponse<unknown>>('unauthorized')
  error.response = {
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config: {} as never,
    data: {
      code: 'AUTH_UNAUTHORIZED',
      message: 'authentication is required',
      data: null,
      traceId: 'trace-auth-401',
      timestamp: '2026-08-10T00:00:00Z',
    },
  }
  return error
}

describe('HTTP contract handling', () => {
  beforeEach(() => {
    setUnauthorizedHandler(() => undefined)
  })

  it('unwraps an OK Java public envelope', () => {
    expect(unwrap({
      code: 'OK',
      message: 'success',
      data: { value: 42 },
      traceId: 'trace-ok',
      timestamp: '2026-08-10T00:00:00Z',
    })).toEqual({ value: 42 })
  })

  it('rejects a malformed success envelope with null data', () => {
    expect(() => unwrap({
      code: 'OK',
      message: 'success',
      data: null,
      traceId: 'trace-empty',
      timestamp: '2026-08-10T00:00:00Z',
    })).toThrow(ApiClientError)
  })

  it('clears login state and invokes navigation only for HTTP 401', async () => {
    const login: LoginView = {
      accessToken: 'temporary-test-token',
      tokenType: 'Bearer',
      expiresInSeconds: 1800,
      user: {
        userId: '11111111-1111-4111-8111-111111111111',
        username: 'candidate',
        createdAt: '2026-08-10T00:00:00Z',
      },
    }
    saveSession(login)
    const navigate = vi.fn()
    setUnauthorizedHandler(navigate)

    await expect(handleResponseError(unauthorizedError())).rejects.toMatchObject({
      code: 'AUTH_UNAUTHORIZED',
      traceId: 'trace-auth-401',
    })
    expect(getAccessToken()).toBeNull()
    expect(navigate).toHaveBeenCalledOnce()
  })

  it('renders timeout as UNKNOWN guidance without retry advice', () => {
    const problem = formatApiError(new ApiClientError(
      'AI_SERVICE_TIMEOUT',
      'upstream timeout',
      504,
      'trace-timeout',
    ))

    expect(problem.message).toContain('结果暂时无法确认')
    expect(problem.traceId).toBe('trace-timeout')
  })

  it.each([
    ['AUTH_UNAUTHORIZED', 401, '重新登录'],
    ['AI_EXECUTION_FAILED', 502, 'AI 服务处理失败'],
    ['DOCUMENT_INDEX_FAILED', 502, '文档索引服务处理失败'],
    ['AI_SERVICE_UNAVAILABLE', 503, 'AI 服务暂时不可用'],
    ['RATE_LIMIT_SERVICE_UNAVAILABLE', 503, '限流服务暂时不可用'],
  ])('renders a stable message for %s', (code, status, expected) => {
    const problem = formatApiError(new ApiClientError(
      code,
      'raw server text',
      status,
      `trace-${status}`,
    ))

    expect(problem.message).toContain(expected)
  })

  it('renders Retry-After guidance for HTTP 429', () => {
    const problem = formatApiError(new ApiClientError(
      'RATE_LIMIT_EXCEEDED',
      'rate limited',
      429,
      'trace-429',
      17,
    ))

    expect(problem.message).toContain('17 秒后')
    expect(problem.traceId).toBe('trace-429')
  })
})
