import { http, unwrap } from './http'
import type { ApiResponse, LoginRequest, LoginView, RegisterRequest, UserView } from '../types/api'

export async function register(request: RegisterRequest): Promise<UserView> {
  const response = await http.post<ApiResponse<UserView>>('/auth/register', request)
  return unwrap(response.data)
}

export async function login(request: LoginRequest): Promise<LoginView> {
  const response = await http.post<ApiResponse<LoginView>>('/auth/login', request)
  return unwrap(response.data)
}
