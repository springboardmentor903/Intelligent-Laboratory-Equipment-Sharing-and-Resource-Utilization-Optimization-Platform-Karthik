export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateLoginForm({ email, password }) {
  const trimmedEmail = typeof email === 'string' ? email.trim() : ''
  if (!trimmedEmail) {
    return 'Please enter your email address.'
  }
  if (!EMAIL_PATTERN.test(trimmedEmail)) {
    return 'Please enter a valid email address.'
  }
  if (!password) {
    return 'Please enter your password.'
  }
  return null
}

export function mapLoginError(requestError) {
  if (!requestError?.response) {
    return 'Unable to connect. Please try again later.'
  }

  const { status, data } = requestError.response

  if (status === 403) {
    if (data?.code === 'MEMBERSHIP_PENDING' || data?.message?.toLowerCase().includes('awaiting')) {
      return 'Your institution membership is awaiting administrator approval.'
    }
    if (data?.code === 'MEMBERSHIP_REJECTED' || data?.message?.toLowerCase().includes('rejected')) {
      return 'Your institution membership request was rejected by the administrator.'
    }
    if (data?.message) {
      return data.message
    }
    return 'Access denied. Please contact your administrator.'
  }

  if (status === 400 || status === 401) {
    return 'Invalid email or password. Please try again.'
  }

  if (status >= 500) {
    return 'Unable to connect. Please try again later.'
  }

  return 'Invalid email or password. Please try again.'
}
