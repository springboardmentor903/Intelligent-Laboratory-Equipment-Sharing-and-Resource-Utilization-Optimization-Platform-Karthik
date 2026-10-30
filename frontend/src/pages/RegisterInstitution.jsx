import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

export default function RegisterInstitution() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const [form, setForm] = useState({
    name: '',
    address: '',
    location: '',
    contactEmail: '',
    phoneNumber: '',
    adminFullName: '',
    adminEmail: '',
    adminPassword: '',
    confirmPassword: '',
    adminPhoneNumber: '',
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

    const name = form.name.trim()
    const adminEmail = form.adminEmail.trim().toLowerCase()
    const contactEmail = form.contactEmail.trim().toLowerCase()

    if (!name) {
      setError('Institution name is required.')
      return
    }

    if (!form.adminFullName.trim()) {
      setError('Administrator full name is required.')
      return
    }

    if (!adminEmail) {
      setError('Administrator email is required.')
      return
    }

    if (form.adminPassword.length < 8 || form.adminPassword.length > 72) {
      setError('Administrator password must be between 8 and 72 characters.')
      return
    }

    if (form.adminPassword !== form.confirmPassword) {
      setError('The administrator passwords do not match.')
      return
    }

    setSubmitting(true)

    try {
      const payload = {
        name,
        address: form.address.trim() || null,
        location: form.location.trim() || null,
        contactEmail: contactEmail || null,
        phoneNumber: form.phoneNumber.trim() || null,
        adminFullName: form.adminFullName.trim(),
        adminEmail,
        adminPassword: form.adminPassword,
        adminPhoneNumber: form.adminPhoneNumber.trim() || null,
      }

      const response = await api.post('/api/institutions/register', payload)

      setSuccess(
        response.data?.message ||
          'Institution registered successfully. You can now log in with your administrator account.',
      )

      setForm({
        name: '',
        address: '',
        location: '',
        contactEmail: '',
        phoneNumber: '',
        adminFullName: '',
        adminEmail: '',
        adminPassword: '',
        confirmPassword: '',
        adminPhoneNumber: '',
      })
    } catch (err) {
      const serverMessage =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Registration failed. Please check your details and try again.'
      setError(serverMessage)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card app-surface" style={{ maxWidth: '640px' }}>
        <div className="auth-heading">
          <div className="auth-logo" aria-hidden="true">
            🏛
          </div>
          <span className="eyebrow">INSTITUTION ONBOARDING</span>
          <h1>Register your institution</h1>
          <p>
            Create an institution profile and set up the primary Institution
            Administrator account.
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
          <h3 style={{ margin: '0.5rem 0 0.25rem 0', color: 'var(--text-primary)' }}>
            Institution details
          </h3>

          <div className="form-field">
            <label htmlFor="inst-name">College / Institution name *</label>
            <input
              id="inst-name"
              name="name"
              maxLength={150}
              placeholder="e.g., Malla Reddy Engineering College"
              value={form.name}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div className="form-field">
              <label htmlFor="inst-location">City / Campus location</label>
              <input
                id="inst-location"
                name="location"
                maxLength={150}
                placeholder="e.g., Hyderabad, Telangana"
                value={form.location}
                onChange={handleChange}
                disabled={submitting}
              />
            </div>
            <div className="form-field">
              <label htmlFor="inst-phone">Official phone</label>
              <input
                id="inst-phone"
                name="phoneNumber"
                type="tel"
                maxLength={30}
                placeholder="e.g., +91 40 1234 5678"
                value={form.phoneNumber}
                onChange={handleChange}
                disabled={submitting}
              />
            </div>
          </div>

          <div className="form-field">
            <label htmlFor="inst-address">Campus address</label>
            <input
              id="inst-address"
              name="address"
              maxLength={255}
              placeholder="e.g., Maisammaguda, Secunderabad"
              value={form.address}
              onChange={handleChange}
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="inst-contact-email">Official contact email</label>
            <input
              id="inst-contact-email"
              name="contactEmail"
              type="email"
              maxLength={180}
              placeholder="e.g., info@mrec.ac.in"
              value={form.contactEmail}
              onChange={handleChange}
              disabled={submitting}
            />
          </div>

          <h3 style={{ margin: '1rem 0 0.25rem 0', color: 'var(--text-primary)' }}>
            Primary Institution Administrator
          </h3>
          <p className="field-hint" style={{ marginBottom: '0.5rem' }}>
            This account will manage departments, equipment, and member approvals for this college.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div className="form-field">
              <label htmlFor="admin-full-name">Admin full name *</label>
              <input
                id="admin-full-name"
                name="adminFullName"
                maxLength={150}
                placeholder="e.g., Dr. Ramesh Sharma"
                value={form.adminFullName}
                onChange={handleChange}
                required
                disabled={submitting}
              />
            </div>
            <div className="form-field">
              <label htmlFor="admin-phone">Admin phone</label>
              <input
                id="admin-phone"
                name="adminPhoneNumber"
                type="tel"
                maxLength={30}
                placeholder="e.g., +91 98765 43210"
                value={form.adminPhoneNumber}
                onChange={handleChange}
                disabled={submitting}
              />
            </div>
          </div>

          <div className="form-field">
            <label htmlFor="admin-email">Admin login email *</label>
            <input
              id="admin-email"
              name="adminEmail"
              type="email"
              maxLength={180}
              placeholder="admin@college.ac.in"
              value={form.adminEmail}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="admin-password">Admin password *</label>
            <div className="password-input-wrapper">
              <input
                id="admin-password"
                name="adminPassword"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                placeholder="At least 8 characters"
                value={form.adminPassword}
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
            <label htmlFor="admin-confirm-password">Confirm admin password *</label>
            <input
              id="admin-confirm-password"
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
            style={{ width: '100%', marginTop: '0.75rem' }}
            disabled={submitting}
          >
            {submitting ? 'Registering institution…' : 'Register institution'}
          </button>
        </form>

        <div className="auth-footer" style={{ marginTop: '1.25rem' }}>
          <p>
            Looking to register as a student or faculty member?{' '}
            <Link to="/register">Individual user registration</Link>
          </p>
          <p>
            Already registered? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </section>
    </div>
  )
}
