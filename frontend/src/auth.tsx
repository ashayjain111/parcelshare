import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, getToken, setToken } from './lib'
import type { User } from './lib'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (v: { email: string; password: string; fullName: string; phone: string }) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState<boolean>(!!getToken())

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setUser(null)
      return
    }
    try {
      setUser(await api<User>('/me'))
    } catch {
      setUser(null)
    }
  }, [])

  useEffect(() => {
    refresh().finally(() => setLoading(false))
    const onLogout = () => setUser(null)
    window.addEventListener('ps-logout', onLogout)
    return () => window.removeEventListener('ps-logout', onLogout)
  }, [refresh])

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      refresh,
      login: async (email, password) => {
        const r = await api<{ token: string; user: User }>('/auth/login', { body: { email, password } })
        setToken(r.token)
        setUser(r.user)
      },
      register: async (v) => {
        const r = await api<{ token: string; user: User }>('/auth/register', { body: v })
        setToken(r.token)
        setUser(r.user)
      },
      logout: () => {
        setToken(null)
        setUser(null)
      },
    }),
    [user, loading, refresh],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}
