import { clearSession } from '../context/authSession.js'

export function handleApiError(error, {
  storage = globalThis.localStorage,
  windowObject = globalThis.window,
} = {}) {
  const status = error.response?.status

  if (status === 401) {
    clearSession(storage)
    if (windowObject) {
      windowObject.dispatchEvent(new Event('lab-auth-unauthorized'))
      if (windowObject.location.pathname !== '/login') {
        windowObject.location.replace('/login')
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
