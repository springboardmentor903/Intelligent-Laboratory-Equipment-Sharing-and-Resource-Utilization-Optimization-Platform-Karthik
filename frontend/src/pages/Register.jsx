
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

export default function Register() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const [form, setForm] = useState({
    institutionId: '',
    email: '',
    password: '',
    confirmPassword: '',
  })

  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  function handleChange(event) {
    const { name, value } = event.target

    setForm((current) => ({
      ...current,
      [name]: value,
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSuccess('')

    const email = form.email.trim().toLowerCase()
    const institutionId = Number(form.institutionId)

    if (!Number.isSafeInteger(institutionId) || institutionId <= 0) {
      setError('Enter a valid institution ID.')
      return
    }

    if (form.password.length < 8 || form.password.length > 72) {
      setError('Password must contain between 8 and 72 characters.')
      return
    }

    if (form.password !== form.confirmPassword) {
      setError('The passwords do not match.')
      return
    }

    setSubmitting(true)

    try {
      const response = await api.post('/api/users/register', {
        institutionId,
        email,
        password: form.password,
      })

      setSuccess(
        response.data?.message ||
          'Registration successful. You can now sign in.',
      )

      setForm({
        institutionId: '',
        email: '',
        password: '',
        confirmPassword: '',
      })
    } catch (err) {
      const status = err.response?.status
      const data = err.response?.data
      const serverMessage =
        typeof data?.message === 'string' ? data.message : ''

      if (!err.response) {
        setError(
          'Unable to connect to the server. Check that the backend is running.',
        )
      } else if (
        status === 409 ||
        /already exists|duplicate email|email.*registered/i.test(serverMessage)
      ) {
        setError('An account with this email already exists.')
      } else if (status === 400) {
        setError(
          serverMessage ||
            'Please check your email, password, and institution ID.',
        )
      } else {
        setError(
          serverMessage ||
            'Registration failed. Please check your details and try again.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card app-surface">
        <div className="auth-heading">
          <div className="auth-logo" aria-hidden="true">
            L
          </div>

          <span className="eyebrow">LAB RESOURCE PLATFORM</span>
          <h1>Create your account</h1>
          <p>Join your institution's laboratory resource platform.</p>
        </div>

        {error && (
          <div className="auth-alert" role="alert">
            <span aria-hidden="true">⚠</span>
            {error}
          </div>
        )}

        {success && (
          <div className="auth-success" role="status">
            <span aria-hidden="true">✓</span>
            <div>
              <p>{success}</p>
              <button
                type="button"
                className="register-login-link"
                onClick={() => navigate('/login')}
              >
                Continue to Login →
              </button>
            </div>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label htmlFor="register-institution">Institution ID</label>
            <input
              id="register-institution"
              name="institutionId"
              type="number"
              min="1"
              step="1"
              placeholder="Enter your institution ID"
              value={form.institutionId}
              onChange={handleChange}
              required
              disabled={submitting}
            />
            <small className="field-hint">
              Use the ID of an institution already registered in the system.
            </small>
          </div>

          <div className="form-field">
            <label htmlFor="register-email">Email address</label>
            <input
              id="register-email"
              name="email"
              type="email"
              autoComplete="email"
              maxLength={180}
              placeholder="you@example.com"
              value={form.email}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="register-password">Password</label>
            <div className="password-input-wrapper">
              <input
                id="register-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                placeholder="At least 8 characters"
                value={form.password}
                onChange={handleChange}
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
          </div>

          <div className="form-field">
            <label htmlFor="register-confirm-password">
              Confirm password
            </label>
            <input
              id="register-confirm-password"
              name="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Re-enter your password"
              value={form.confirmPassword}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <button
            type="submit"
            className="button button-primary auth-submit"
            disabled={submitting}
          >
            {submitting ? 'Creating account…' : 'Create account'}
            {!submitting && <span aria-hidden="true">→</span>}
          </button>
        </form>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>

        <div className="auth-security-note">
          <span aria-hidden="true">🔒</span>
          Your password is securely hashed by the backend.
        </div>
      </section>
    </div>
  )
}
