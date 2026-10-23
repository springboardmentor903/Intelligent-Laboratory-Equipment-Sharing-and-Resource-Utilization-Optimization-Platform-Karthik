
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react'
import api from '../api/axiosInstance'

const AuthContext = createContext(null)

function getStoredUser() {
  try {
    const storedUser = localStorage.getItem('user')
    return storedUser ? JSON.parse(storedUser) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(getStoredUser)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('token')

    if (!token) {
      setUserState(null)
      setLoading(false)
      return
    }

    // A stored user is restored locally. The backend can be used
    // later to validate the token against a protected endpoint.
    setUserState(getStoredUser())
    setLoading(false)
  }, [])

  const setUser = useCallback((nextUser) => {
    setUserState(nextUser)

    try {
      if (nextUser) {
        localStorage.setItem('user', JSON.stringify(nextUser))
      } else {
        localStorage.removeItem('user')
      }
    } catch {
      // Keep the current session usable if browser storage is unavailable.
    }
  }, [])

  const login = useCallback(async (credentials) => {
    const response = await api.post('/api/auth/login', credentials)
    const data = response.data

    if (!data.token) {
      throw new Error('The server response did not contain an access token.')
    }

    localStorage.setItem('token', data.token)

    const authenticatedUser = {
      id: data.id,
      email: data.email,
      institutionId: data.institutionId,
    }

    setUser(authenticatedUser)
    return data
  }, [setUser])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    setUser(null)
  }, [setUser])

  const value = {
    user,
    setUser,
    login,
    logout,
    loading,
    isAuthenticated: Boolean(user && localStorage.getItem('token')),
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
