import { computed, reactive } from 'vue'
import type { LoginView, UserView } from '../types/api'

const STORAGE_KEY = 'insurance-ai.auth'

interface StoredSession {
  accessToken: string
  user: UserView
}

function isUserView(value: unknown): value is UserView {
  if (value === null || typeof value !== 'object') return false
  const user = value as Partial<UserView>
  return typeof user.userId === 'string'
    && typeof user.username === 'string'
    && typeof user.createdAt === 'string'
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (typeof parsed.accessToken !== 'string'
      || parsed.accessToken.length === 0
      || !isUserView(parsed.user)) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return { accessToken: parsed.accessToken, user: parsed.user }
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

const stored = readStoredSession()

export const authState = reactive<StoredSession>({
  accessToken: stored?.accessToken ?? '',
  user: stored?.user ?? { userId: '', username: '', createdAt: '' },
})

export const isAuthenticated = computed(() => authState.accessToken.length > 0)

export function saveSession(login: LoginView): void {
  authState.accessToken = login.accessToken
  authState.user = login.user
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
    accessToken: login.accessToken,
    user: login.user,
  } satisfies StoredSession))
}

export function clearSession(): void {
  authState.accessToken = ''
  authState.user = { userId: '', username: '', createdAt: '' }
  sessionStorage.removeItem(STORAGE_KEY)
}

export function getAccessToken(): string | null {
  return authState.accessToken || null
}
