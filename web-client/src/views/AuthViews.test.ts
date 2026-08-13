import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, clearSession } from '../auth/session'
import LoginView from './LoginView.vue'
import RegisterView from './RegisterView.vue'

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('../api/auth', () => ({ login: mocks.login, register: mocks.register }))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: mocks.replace }),
}))

describe('authentication views', () => {
  beforeEach(() => {
    clearSession()
  })

  it('saves a successful login and enters the protected chat page', async () => {
    mocks.login.mockResolvedValue({
      accessToken: 'test-login-token',
      tokenType: 'Bearer',
      expiresInSeconds: 1800,
      user: {
        userId: '11111111-1111-4111-8111-111111111111',
        username: 'candidate',
        createdAt: '2026-08-10T00:00:00Z',
      },
    })
    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('candidate')
    await inputs[1]?.setValue('Password123!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.login).toHaveBeenCalledWith({ username: 'candidate', password: 'Password123!' })
    expect(getAccessToken()).toBe('test-login-token')
    expect(mocks.replace).toHaveBeenCalledWith('/chat')
  })

  it('rejects mismatched registration passwords before calling Java', async () => {
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('candidate')
    await inputs[1]?.setValue('Password123!')
    await inputs[2]?.setValue('Different123!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })

  it('returns a registered user to login without storing the password', async () => {
    mocks.register.mockResolvedValue({
      userId: '11111111-1111-4111-8111-111111111111',
      username: 'candidate',
      createdAt: '2026-08-10T00:00:00Z',
    })
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('candidate')
    await inputs[1]?.setValue('Password123!')
    await inputs[2]?.setValue('Password123!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.replace).toHaveBeenCalledWith({
      name: 'login',
      query: { username: 'candidate' },
    })
    expect(sessionStorage.getItem('insurance-ai.auth')).toBeNull()
  })
})
