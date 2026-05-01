import { createContext, useContext, useState, type ReactNode } from 'react'
import { API_BASE } from '../api'

interface AuthState {
  token: string | null
  userId: string | null
  tenantId: string | null
  email: string | null
}

interface AuthContextValue extends AuthState {
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue>(null!)

function decodePayload(token: string): Omit<AuthState, 'token'> | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return { userId: payload.sub, tenantId: payload.tenantId, email: payload.email }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState>(() => {
    const token = localStorage.getItem('llm-gateway-jwt')
    if (token) {
      const payload = decodePayload(token)
      if (payload) return { token, ...payload }
    }
    return { token: null, userId: null, tenantId: null, email: null }
  })

  async function login(email: string, password: string) {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
    })
    if (res.status === 401) throw new Error('Invalid email or password')
    if (!res.ok) throw new Error('Login failed')
    const data: { token: string; userId: string; tenantId: string; email: string } = await res.json()
    localStorage.setItem('llm-gateway-jwt', data.token)
    setAuth({ token: data.token, userId: data.userId, tenantId: data.tenantId, email: data.email })
  }

  async function register(email: string, password: string) {
    const res = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
    })
    if (res.status === 409) throw new Error('An account with that email already exists')
    if (res.status === 400) throw new Error('Password must be at least 8 characters')
    if (!res.ok) throw new Error('Registration failed — please try again')
    // Do not auto-login: user must sign in explicitly after registration
  }

  function logout() {
    localStorage.removeItem('llm-gateway-jwt')
    setAuth({ token: null, userId: null, tenantId: null, email: null })
    fetch(`${API_BASE}/auth/logout`, { method: 'POST' }).catch(() => {})
  }

  return (
    <AuthContext.Provider value={{ ...auth, login, register, logout, isAuthenticated: !!auth.token }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
