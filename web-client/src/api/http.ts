import axios, { AxiosError } from 'axios'
import type { ApiResponse } from '../types/api'
import { clearSession, getAccessToken } from '../auth/session'

export class ApiClientError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number | null,
    public readonly traceId: string | null,
    public readonly retryAfterSeconds: number | null = null,
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

let unauthorizedHandler: () => void = () => undefined

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 65_000,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use((response) => response, handleResponseError)

export function handleResponseError(error: unknown): Promise<never> {
  if (axios.isAxiosError(error) && error.response?.status === 401) {
    clearSession()
    unauthorizedHandler()
  }
  return Promise.reject(toApiClientError(error))
}

export function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 'OK' || response.data === null) {
    throw new ApiClientError(
      response.code || 'INVALID_RESPONSE',
      response.message || '服务返回了无效响应',
      null,
      response.traceId || null,
    )
  }
  return response.data
}

export function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) return error
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const envelope = error.response?.data
    if (envelope && typeof envelope === 'object') {
      return new ApiClientError(
        envelope.code || 'REQUEST_FAILED',
        envelope.message || '请求失败，请稍后重试',
        error.response?.status ?? null,
        envelope.traceId || null,
        parseRetryAfter(error.response?.headers?.['retry-after']),
      )
    }
    if (error.code === AxiosError.ERR_CANCELED) {
      return new ApiClientError('REQUEST_CANCELED', '请求已取消', null, null)
    }
    if (error.code === AxiosError.ECONNABORTED) {
      return new ApiClientError('CLIENT_TIMEOUT', '请求等待超时，结果可能暂时无法确认', null, null)
    }
    return new ApiClientError('NETWORK_ERROR', '无法连接 Java 服务', null, null)
  }
  return new ApiClientError('UNKNOWN_ERROR', '发生未知错误', null, null)
}

export function formatApiError(error: unknown): { message: string; traceId: string | null } {
  const problem = toApiClientError(error)
  const stableMessages: Record<string, string> = {
    AUTH_UNAUTHORIZED: '登录状态已失效，请重新登录。',
    AI_EXECUTION_FAILED: 'AI 服务处理失败，请稍后再试。',
    DOCUMENT_INDEX_FAILED: '文档索引服务处理失败，请稍后再试。',
    AI_SERVICE_UNAVAILABLE: 'AI 服务暂时不可用，请稍后再试。',
    RATE_LIMIT_SERVICE_UNAVAILABLE: '限流服务暂时不可用，请稍后再试。',
    AI_SERVICE_TIMEOUT: '结果暂时无法确认，请勿自动重发。',
    CLIENT_TIMEOUT: '结果暂时无法确认，请勿自动重发。',
  }
  let message = stableMessages[problem.code] || problem.message
  if (problem.code === 'RATE_LIMIT_EXCEEDED') {
    message = problem.retryAfterSeconds === null
      ? '请求过于频繁，请稍后再试。'
      : `请求过于频繁，请在 ${problem.retryAfterSeconds} 秒后再试。`
  }
  return { message, traceId: problem.traceId }
}

function parseRetryAfter(value: unknown): number | null {
  const seconds = Number(value)
  return Number.isInteger(seconds) && seconds >= 0 ? seconds : null
}
