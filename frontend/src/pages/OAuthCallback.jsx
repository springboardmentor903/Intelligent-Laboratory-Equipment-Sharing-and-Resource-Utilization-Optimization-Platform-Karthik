import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getOAuthCallbackResult, oauthFailureMessage } from '../access/oauthLogin.js'
import { useAuth } from '../context/AuthContext'

export default function OAuthCallback() {
  const { completeOAuthLogin } = useAuth()
  const navigate = useNavigate()
  const handled = useRef(false)
  const [callbackResult] = useState(() => getOAuthCallbackResult(window.location.hash))
  const [exchangeError, setExchangeError] = useState('')
  const error = callbackResult.error || (!callbackResult.code
    ? 'Google sign-in could not be completed. Please try again.'
    : exchangeError)

  useEffect(() => {
    if (handled.current) return
    handled.current = true
    const { code } = callbackResult
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)

    if (!code || callbackResult.error) return

    completeOAuthLogin(code)
      .then(() => navigate('/dashboard', { replace: true }))
      .catch((requestError) => setExchangeError(
        requestError.response?.data?.message || requestError.message || 'Google sign-in could not be completed.',
      ))
  }, [callbackResult, completeOAuthLogin, navigate])

  return (
    <section className="auth-page">
      <div className="auth-card app-surface">
        {error ? (
          <>
            <h1>Sign-in unavailable</h1>
            <div className="auth-alert" role="alert">{callbackResult.error ? oauthFailureMessage(callbackResult.error) : error}</div>
            <Link className="auth-submit" to="/login">Return to sign in</Link>
          </>
        ) : (
          <div className="module-state" role="status">
            <span className="loading-spinner" />
            <p>Completing Google sign-in…</p>
          </div>
        )}
      </div>
    </section>
  )
}
