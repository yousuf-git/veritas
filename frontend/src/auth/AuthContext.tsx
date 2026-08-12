import { createContext, use, useCallback, useMemo, useState, type ReactNode } from 'react'
import { api, clearToken, storeToken, storedToken } from '../api/client'
import type { LoginResponse } from '../api/types'

interface AuthState {
  username: string
  displayName: string
  role: string
  permissions: string[]
}

interface AuthContextValue {
  user: AuthState | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  can: (permission: string) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

const USER_KEY = 'recon.user'

function restoreUser(): AuthState | null {
  if (!storedToken()) {
    return null
  }
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as AuthState) : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthState | null>(restoreUser)

  const login = useCallback(async (username: string, password: string) => {
    const response = await api<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { username, password },
    })

    storeToken(response.accessToken)
    const state: AuthState = {
      username: response.username,
      displayName: response.displayName,
      role: response.role,
      permissions: response.permissions,
    }
    localStorage.setItem(USER_KEY, JSON.stringify(state))
    setUser(state)
  }, [])

  const logout = useCallback(() => {
    clearToken()
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login,
      logout,
      can: (permission: string) => user?.permissions.includes(permission) ?? false,
    }),
    [user, login, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthContextValue {
  const value = use(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
