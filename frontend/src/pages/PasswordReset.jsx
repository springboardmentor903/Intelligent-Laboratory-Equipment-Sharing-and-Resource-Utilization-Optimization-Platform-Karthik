
import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'

function getErrorMessage(error, fallback) {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    fallback
  )
}

export default function PasswordReset() {
  const [step, setStep] = useState('request')
  const [email, setEmail] = useState('')
  const [token, setToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [resetToken, setResetToken] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleRequest(event) {
    event.preventDefault()

    if (submitting) return

    setError('')
    setMessage('')
    setResetToken('')
    setSubmitting(true)

    try {
      const response = await api.post(
        '/api/auth/password-reset/request',
        { email: email.trim().toLowerCase() },
      )

      setMessage(
        response.data?.message ||
          'If an account exists for that email, reset instructions will be sent.',
      )

      if (response.data?.resetToken) {
        setResetToken(response.data.resetToken)
        setToken(response.data.resetToken)
      } else {
        setToken('')
      }
    } catch (requestError) {
      setError(
        getErrorMessage(
          requestError,
          'Unable to submit the password reset request. Please try again.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleConfirm(event) {
    event.preventDefault()

    if (submitting) return

    setError('')
    setMessage('')

    if (newPassword.length < 8 || newPassword.length > 72) {
      setError('Your new password must contain between 8 and 72 characters.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('The passwords do not match.')
      return
    }

    if (!token.trim()) {
      setError('Enter the password reset token.')
      return
    }

    setSubmitting(true)

    try {
      const response = await api.post(
        '/api/auth/password-reset/confirm',
        {
          token: token.trim(),
          newPassword,
        },
      )

      setMessage(
        response.data?.message ||
          'Password reset successful. You can now sign in.',
      )
      setStep('complete')
      setNewPassword('')
      setConfirmPassword('')
      setToken('')
      setResetToken('')
    } catch (requestError) {
      setError(
        getErrorMessage(
          requestError,
          'Password reset failed. The token may be invalid or expired.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  function returnToRequest() {
    setStep('request')
    setError('')
    setMessage('')
    setNewPassword('')
    setConfirmPassword('')
    setShowPassword(false)
  }

  return (
    <section className="auth-page">
      <div className="auth-card app-surface">
        <div className="auth-heading">
          <span className="auth-icon" aria-hidden="true">
            L
          </span>

          <p className="page-eyebrow">ACCOUNT RECOVERY</p>

          <h1>
            {step === 'complete'
              ? 'Password updated'
              : 'Reset your password'}
          </h1>

          <p className="muted-text">
            {step === 'request'
              ? 'Enter your account email to request a password reset.'
              : step === 'confirm'
                ? 'Enter your reset token and choose a new password.'
                : 'Your password has been changed successfully.'}
          </p>
        </div>

        {error && (
          <div className="auth-alert" role="alert">
            {error}
          </div>
        )}

        {message && (
          <div className="auth-success" role="status">
            {message}
          </div>
        )}

        {step === 'request' && (
          <form className="auth-form" onSubmit={handleRequest}>
            <label htmlFor="reset-email">Email address</label>

            <input
              id="reset-email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value)
                setError('')
              }}
              required
              disabled={submitting}
            />

            <button
              type="submit"
              className="auth-submit"
              disabled={submitting}
            >
              {submitting ? 'Submitting…' : 'Request reset'}
            </button>

            {message && (
              <button
                type="button"
                className="secondary-button reset-next-button"
                onClick={() => {
                  setStep('confirm')
                  setError('')
                  setMessage('')
                }}
                disabled={submitting}
              >
                {resetToken ? 'Continue with reset token' : 'Enter reset token'}
              </button>
            )}

            {resetToken && (
              <div className="reset-token-notice">
                <strong>Development reset token</strong>
                <p>
                  This token is exposed by the backend for local development.
                  Keep it private and do not share it.
                </p>
                <code>{resetToken}</code>
              </div>
            )}
          </form>
        )}

        {step === 'confirm' && (
          <form className="auth-form" onSubmit={handleConfirm}>
            <label htmlFor="reset-token">Reset token</label>

            <input
              id="reset-token"
              type="text"
              autoComplete="off"
              placeholder="Paste your reset token"
              value={token}
              onChange={(event) => {
                setToken(event.target.value)
                setError('')
              }}
              required
              disabled={submitting}
            />

            <label htmlFor="reset-new-password">New password</label>

            <div className="password-input-wrapper">
              <input
                id="reset-new-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                placeholder="At least 8 characters"
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value)
                  setError('')
                }}
                required
                disabled={submitting}
              />

              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((current) => !current)}
                aria-label={showPassword ? 'Hide passwords' : 'Show passwords'}
                disabled={submitting}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>

            <label htmlFor="reset-confirm-password">
              Confirm new password
            </label>

            <input
              id="reset-confirm-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              value={confirmPassword}
              onChange={(event) => {
                setConfirmPassword(event.target.value)
                setError('')
              }}
              required
              disabled={submitting}
            />

            <button
              type="submit"
              className="auth-submit"
              disabled={submitting}
            >
              {submitting ? 'Updating password…' : 'Update password'}
            </button>

            <button
              type="button"
              className="secondary-button reset-next-button"
              onClick={returnToRequest}
              disabled={submitting}
            >
              Back to reset request
            </button>
          </form>
        )}

        {step === 'complete' && (
          <Link to="/login" className="auth-submit reset-login-link">
            Continue to Login →
          </Link>
        )}

        {step !== 'complete' && (
          <p className="auth-switch">
            Remember your password? <Link to="/login">Sign in</Link>
          </p>
        )}
      </div>
    </section>
  )
}
