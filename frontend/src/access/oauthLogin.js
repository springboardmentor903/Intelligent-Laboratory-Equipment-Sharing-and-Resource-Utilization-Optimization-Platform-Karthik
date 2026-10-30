export function getOAuthCallbackResult(hash) {
  const params = new URLSearchParams(String(hash || '').replace(/^#/, ''))
  return {
    code: params.get('code') || '',
    error: params.get('oauth_error') || '',
  }
}

export function googleAuthorizationUrl(apiBaseUrl) {
  return `${String(apiBaseUrl || 'http://localhost:8080').replace(/\/+$/, '')}/oauth2/authorization/google`
}

export function oauthFailureMessage(error) {
  if (error === 'google_login_failed') {
    return 'Google sign-in could not be completed. Use an approved platform account or try again.'
  }
  return 'Google sign-in could not be completed. Please try again.'
}
