import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

export default function ApplySystemAdmin() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const [form, setForm] = useState({
    fullName: '',
    email: '',
    phoneNumber: '',
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

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')

    const email = form.email.trim().toLowerCase()

    if (!form.fullName.trim()) {
      setError('Full name is required.')
      return
    }

    if (!email) {
      setError('Email address is required.')
      return
    }

    if (form.password.length < 8 || form.password.length > 72) {
      setError('Password must be between 8 and 72 characters.')
      return
    }

    if (form.password !== form.confirmPassword) {
      setError('The passwords do not match.')
      return
    }

    setSubmitting(true)

    try {
      const payload = {
        fullName: form.fullName.trim(),
        email,
        phoneNumber: form.phoneNumber.trim() || null,
        password: form.password,
      }

      const res = await api.post('/api/users/apply-system-admin', payload)

      setSuccess(
        res.data?.message ||
          'Application submitted successfully. An existing System Administrator must review and approve your application before access is granted.',
      )

      setForm({
        fullName: '',
        email: '',
        phoneNumber: '',
        password: '',
        confirmPassword: '',
      })
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          'Application submission failed. Please check your details and try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card app-surface" style={{ maxWidth: '540px' }}>
        <div className="auth-heading">
          <div className="auth-logo" aria-hidden="true">
            🛡️
          </div>
          <span className="eyebrow">PLATFORM GOVERNANCE</span>
          <h1>System Admin Application</h1>
          <p>
            Request platform-level System Administrator access. Applications are
            held in a pending state until reviewed by an existing authorized System
            Administrator.
          </p>
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
            <label htmlFor="sys-fullname">Full Name *</label>
            <input
              id="sys-fullname"
              name="fullName"
              maxLength={150}
              placeholder="e.g., Dilip Karthik"
              value={form.fullName}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="sys-phone">Phone Number</label>
            <input
              id="sys-phone"
              name="phoneNumber"
              type="tel"
              maxLength={30}
              placeholder="e.g., +91 98765 43210"
              value={form.phoneNumber}
              onChange={handleChange}
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="sys-email">Official Email address *</label>
            <input
              id="sys-email"
              name="email"
              type="email"
              maxLength={180}
              placeholder="admin@platform.org"
              value={form.email}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="sys-password">Password *</label>
            <div className="password-input-wrapper">
              <input
                id="sys-password"
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
                className="password-toggle-button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>

          <div className="form-field">
            <label htmlFor="sys-confirm">Confirm password *</label>
            <input
              id="sys-confirm"
              name="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              placeholder="Confirm your password"
              value={form.confirmPassword}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <button
            type="submit"
            className="button button-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={submitting}
          >
            {submitting ? 'Submitting application…' : 'Submit System Admin Application'}
          </button>
        </form>

        <div className="auth-footer" style={{ marginTop: '1.25rem' }}>
          <p>
            Looking for regular user registration?{' '}
            <Link to="/register">Register here</Link>
          </p>
          <p>
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </section>
    </div>
  )
}
