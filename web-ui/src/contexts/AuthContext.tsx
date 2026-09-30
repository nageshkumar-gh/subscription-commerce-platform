import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { AuthSession, Credentials, ProfileUpdate, Registration, User } from '../types'

const SESSION_KEY = 'subscription-auth-session'

type AuthContextValue = {
  user: User | null
  /** Bearer token for the storefront API; null when signed out. */
  accessToken: string | null
  login: (credentials: Credentials) => Promise<void>
  register: (registration: Registration) => Promise<void>
  updateProfile: (profile: ProfileUpdate) => Promise<void>
  deleteProfile: () => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readSession())
  const user = session?.user ?? null

  const saveSession = useCallback((next: AuthSession | null) => {
    setSession(next)
    if (next) localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    else localStorage.removeItem(SESSION_KEY)
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    accessToken: session?.accessToken ?? null,
    login: async (credentials) => saveSession(await api.login(credentials)),
    register: async (registration) => saveSession(await api.register(registration)),
    updateProfile: async (profile) => {
      if (!session) throw new Error('You must be logged in to update your profile.')
      const updated = await api.updateCustomer(session.accessToken, profile)
      saveSession({ ...session, user: updated })
    },
    deleteProfile: async () => {
      if (!session) throw new Error('You must be logged in to delete your profile.')
      await api.deleteCustomer(session.accessToken)
      saveSession(null)
    },
    logout: () => saveSession(null),
  }), [saveSession, session, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function readSession(): AuthSession | null {
  try {
    const stored = localStorage.getItem(SESSION_KEY)
    return stored ? JSON.parse(stored) as AuthSession : null
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
