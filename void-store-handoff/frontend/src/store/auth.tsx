import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { hadSession, http, logout as apiLogout, onSession, refreshSession, setSession, type Session, type SessionUser } from '../api/http'

interface AuthState {
  user: SessionUser | null
  /** False until the first silent refresh finished. */
  ready: boolean
  login: (email: string, password: string) => Promise<SessionUser>
  register: (name: string, email: string, password: string, phone?: string) => Promise<SessionUser>
  logout: () => Promise<void>
  setUser: (u: SessionUser) => void
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<SessionUser | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const off = onSession((s) => setUserState(s?.user ?? null))
    if (hadSession()) refreshSession().finally(() => setReady(true))
    else setReady(true)
    return () => {
      off()
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const s = await http.post<Session>('/auth/login', { email, password })
    setSession(s)
    return s.user
  }, [])

  const register = useCallback(async (name: string, email: string, password: string, phone?: string) => {
    const s = await http.post<Session>('/auth/register', { name, email, password, phone })
    setSession(s)
    return s.user
  }, [])

  const logout = useCallback(() => apiLogout(), [])

  const value = useMemo(
    () => ({ user, ready, login, register, logout, setUser: setUserState }),
    [user, ready, login, register, logout],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth() {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}

export const isStaff = (u: SessionUser | null) => !!u && u.role !== 'CUSTOMER'
