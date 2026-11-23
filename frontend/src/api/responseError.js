import { clearSession } from '../context/authSession.js'

export function isTokenExpired(token) {
  if (!token) return true
  try {
    const parts = token.split('.')
    if (parts.length < 2) return true
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(
      typeof atob === 'function'
        ? atob(base64)
        : Buffer.from(base64, 'base64').toString('utf-8')
    )
    if (payload.exp && Date.now() >= payload.exp * 1000) {
      return true
    }
    return false
  } catch {
    return false
  }
}

export function handleApiError(error, {
  storage = globalThis.localStorage,
  windowObject = globalThis.window,
} = {}) {
  const status = error.response?.status
  const url = error.config?.url || ''

  if (status === 401) {
    const currentToken = storage?.getItem?.('token')
    // Clear session and redirect to /login only when:
    // 1. It is a core auth validation check (e.g. /api/auth/me, /api/auth/user), OR
    // 2. The token is missing or expired, OR
    // 3. No request config url is present (direct test invocation or generic unauthenticated probe), OR
    // 4. The backend explicitly indicated an expired/invalid token error
    const isCoreAuthUrl = !url || url.includes('/api/auth/me') || url.includes('/api/auth/user')
    const tokenExpired = !currentToken || isTokenExpired(currentToken)
    const explicitTokenError = error.response?.data?.message?.toLowerCase().includes('token')
      || error.response?.data?.error?.toLowerCase().includes('token')
      || error.response?.data?.code === 'TOKEN_EXPIRED'

    if (isCoreAuthUrl || tokenExpired || explicitTokenError) {
      clearSession(storage)
      if (windowObject) {
        windowObject.dispatchEvent(new Event('lab-auth-unauthorized'))
        if (windowObject.location?.pathname !== '/login') {
          windowObject.location?.replace?.('/login')
        }
      }
    } else {
      // Do not wipe valid tokens on role-specific sub-requests
      error.response.data = {
        ...(error.response.data && typeof error.response.data === 'object'
          ? error.response.data
          : {}),
        message: error.response.data?.message || 'Access unauthorized for this specific resource.',
      }
    }
  } else if (status === 403) {
    if (
      error.response.data?.code === 'MEMBERSHIP_PENDING' ||
      error.response.data?.code === 'MEMBERSHIP_REJECTED'
    ) {
      return error
    }
    // Page-level error handlers display this single access-denied message.
    error.response.data = {
      ...(error.response.data && typeof error.response.data === 'object'
        ? error.response.data
        : {}),
      message: 'Access denied. Your account is not permitted to perform this action.',
    }
  }

  return error
}
