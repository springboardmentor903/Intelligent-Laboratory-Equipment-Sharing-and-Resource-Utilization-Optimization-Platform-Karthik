import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { googleAuthorizationUrl, getOAuthCallbackResult, oauthFailureMessage } from '../access/oauthLogin.js'
import { getLoginDestination } from '../access/homeAccess.js'
import { validateLoginForm, mapLoginError } from '../access/loginValidation.js'

export default function Login() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState(() => {
    const { error: oauthError } = getOAuthCallbackResult(window.location.hash)
    return oauthError ? oauthFailureMessage(oauthError) : ''
  })
  const [submitting, setSubmitting] = useState(false)
  const googleOAuthEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true'

  useEffect(() => {
    const { error: oauthError } = getOAuthCallbackResult(window.location.hash)
    if (oauthError) {
      window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
    }
  }, [])

  const destination = getLoginDestination(location.state?.from)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    setError('')

    const validationError = validateLoginForm({ email, password })
    if (validationError) {
      setError(validationError)
      return
    }

    setSubmitting(true)

    try {
      await login({
        email: email.trim(),
        password,
      })

      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(mapLoginError(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-page">
      <div className="auth-card app-surface">
        <div className="auth-heading">
          <span className="auth-icon" aria-hidden="true">
            L
          </span>

          <p className="page-eyebrow">LAB RESOURCE PLATFORM</p>
          <h1>Welcome back</h1>

          <p className="muted-text">
            Sign in to access your laboratory workspace.
          </p>
        </div>

        {error && (
          <div className="auth-alert" role="alert">
            {error}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="login-email">Email address</label>

          <input
            id="login-email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            disabled={submitting}
          />

          <label htmlFor="login-password">Password</label>

          <div className="password-input-wrapper">
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Enter your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              disabled={submitting}
            />

            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword((current) => !current)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              disabled={submitting}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>

          <div className="forgot-password-row">
            <Link to="/password-reset">Forgot password?</Link>
          </div>

          <button
            type="submit"
            className="auth-submit"
            disabled={submitting}
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        {googleOAuthEnabled && (
          <a className="secondary-button google-login-button" href={googleAuthorizationUrl(import.meta.env.VITE_API_BASE_URL)}>
            Continue with Google
          </a>
        )}

        <p className="auth-switch">
          Don't have an account?{' '}
          <Link to="/register">Create an account</Link>
        </p>
      </div>
    </section>
  )
}
