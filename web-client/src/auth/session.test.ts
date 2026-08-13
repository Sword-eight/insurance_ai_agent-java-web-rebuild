import { beforeEach, describe, expect, it } from 'vitest'
import { authState, clearSession, getAccessToken, saveSession } from './session'
import type { LoginView } from '../types/api'

const login: LoginView = {
  accessToken: 'test-token-not-a-real-jwt',
  tokenType: 'Bearer',
  expiresInSeconds: 1800,
  user: {
    userId: '11111111-1111-4111-8111-111111111111',
    username: 'candidate',
    createdAt: '2026-08-10T00:00:00Z',
  },
}

describe('authentication session', () => {
  beforeEach(clearSession)

  it('keeps the access token and public user in sessionStorage', () => {
    saveSession(login)

    expect(getAccessToken()).toBe(login.accessToken)
    expect(authState.user.username).toBe('candidate')
    expect(sessionStorage.getItem('insurance-ai.auth')).not.toContain('password')
  })

  it('clears both reactive and persisted authentication state', () => {
    saveSession(login)
    clearSession()

    expect(getAccessToken()).toBeNull()
    expect(authState.user.userId).toBe('')
    expect(sessionStorage.getItem('insurance-ai.auth')).toBeNull()
  })
})
