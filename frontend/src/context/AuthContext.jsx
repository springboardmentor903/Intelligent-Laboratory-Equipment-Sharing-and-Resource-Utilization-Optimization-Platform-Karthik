
import {
  createContext,
  useContext,
  useEffect,
  useState,
} from 'react'
import api from '../api/axiosInstance'
import { hasValidRoles } from '../access/roleAccess.js'
import {
  clearSession,
  persistSession,
  readSession,
} from './authSession.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession())
  const { token, user } = session
  const loading = false

  useEffect(() => {
    function handleUnauthorized() {
      clearSession()
      setSession({ token: null, user: null })
    }

    window.addEventListener('lab-auth-unauthorized', handleUnauthorized)
    return () =>
      window.removeEventListener('lab-auth-unauthorized', handleUnauthorized)
  }, [])

  function acceptLoginResponse(data) {
    if (!data.token || data.id == null || !data.email || !hasValidRoles(data.roles)) {
      clearSession()
      setSession({ token: null, user: null })
      throw new Error('The server response did not contain a valid authenticated user session and role.')
    }

    const authenticatedUser = {
      id: data.id,
      email: data.email,
      fullName: data.fullName || null,
      institutionId: data.institutionId ?? null,
      departmentId: data.departmentId ?? null,
      roles: data.roles,
    }

    persistSession(localStorage, data.token, authenticatedUser)
    setSession({ token: data.token, user: authenticatedUser })
    return data
  }

  async function login(credentials) {
    const response = await api.post('/api/auth/login', credentials)
    return acceptLoginResponse(response.data)
  }

  async function completeOAuthLogin(code) {
    const response = await api.post('/api/auth/oauth2/exchange', { code })
    return acceptLoginResponse(response.data)
  }

  function logout() {
    clearSession()
    setSession({ token: null, user: null })
  }

  const value = {
    user,
    login,
    completeOAuthLogin,
    logout,
    loading,
    isAuthenticated: Boolean(user && token && hasValidRoles(user.roles)),
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }

  return context
}
