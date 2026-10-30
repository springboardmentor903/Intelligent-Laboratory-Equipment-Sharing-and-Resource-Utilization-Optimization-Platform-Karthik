import { hasValidRoles } from '../access/roleAccess.js'

export { hasValidRoles }

export function readSession(storage = globalThis.localStorage) {
  if (!storage) return { token: null, user: null }

  const token = storage.getItem('token')
  const storedUser = storage.getItem('user')

  if (!token || !storedUser) {
    clearSession(storage)
    return { token: null, user: null }
  }

  try {
    const user = JSON.parse(storedUser)
    if (!user || user.id == null || !user.email || !hasValidRoles(user.roles)) {
      clearSession(storage)
      return { token: null, user: null }
    }
    return { token, user }
  } catch {
    clearSession(storage)
    return { token: null, user: null }
  }
}

export function persistSession(storage, token, user) {
  storage.setItem('token', token)
  storage.setItem('user', JSON.stringify(user))
}

export function clearSession(storage = globalThis.localStorage) {
  if (!storage) return
  storage.removeItem('token')
  storage.removeItem('user')
}

export function bookingsUrlForUser(userId) {
  if (userId == null || userId === '') {
    throw new Error('A signed-in user ID is required to load booking history.')
  }
  return `/api/bookings/user/${encodeURIComponent(userId)}`
}
