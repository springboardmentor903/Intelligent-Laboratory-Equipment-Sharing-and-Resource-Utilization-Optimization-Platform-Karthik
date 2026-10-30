import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

const APPLICANT_ROLES = [
  { value: 'RESEARCHER_STUDENT', label: 'Researcher / Student', description: 'Search equipment and request bookings' },
  { value: 'LAB_TECHNICIAN', label: 'Lab Technician', description: 'Perform equipment operation and maintenance' },
  { value: 'LAB_MANAGER', label: 'Lab Manager', description: 'Manage lab equipment and approve bookings' },
  { value: 'DEPARTMENT_HEAD', label: 'Department Head', description: 'Oversee department labs and equipment' },
  { value: 'INSTITUTION_ADMIN', label: 'Institution Administrator', description: 'Manage college departments, users, and equipment (requires admin approval)' },
]

export default function Register() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const [institutions, setInstitutions] = useState([])
  const [departments, setDepartments] = useState([])
  const [loadingInstitutions, setLoadingInstitutions] = useState(true)
  const [loadingDepartments, setLoadingDepartments] = useState(false)

  const [form, setForm] = useState({
    institutionId: '',
    departmentId: '',
    requestedRole: 'RESEARCHER_STUDENT',
    fullName: '',
    phoneNumber: '',
    email: '',
    password: '',
    confirmPassword: '',
  })

  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true
    api.get('/api/institutions')
      .then((res) => {
        if (active && Array.isArray(res.data)) {
          setInstitutions(res.data)
          setForm((prev) => (!prev.institutionId && res.data.length > 0 ? { ...prev, institutionId: String(res.data[0].id) } : prev))
        }
      })
      .catch(() => {
        if (active) setError('Could not load institution list. Please refresh the page.')
      })
      .finally(() => {
        if (active) setLoadingInstitutions(false)
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!form.institutionId) return
    let active = true
    api.get(`/api/institutions/${form.institutionId}/departments/public`)
      .then((res) => {
        if (active && Array.isArray(res.data)) {
          setDepartments(res.data)
        }
      })
      .catch(() => {
        if (active) setDepartments([])
      })
      .finally(() => {
        if (active) setLoadingDepartments(false)
      })
    return () => { active = false }
  }, [form.institutionId])

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({
      ...current,
      [name]: value,
      ...(name === 'institutionId' ? { departmentId: '' } : {}),
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSuccess('')

    const email = form.email.trim().toLowerCase()
    const institutionId = Number(form.institutionId)

    if (!Number.isSafeInteger(institutionId) || institutionId <= 0) {
      setError('Please select a valid institution.')
      return
    }

    if (!form.fullName.trim()) {
      setError('Full name is required.')
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
      const payload = {
        institutionId,
        departmentId: form.departmentId ? Number(form.departmentId) : null,
        requestedRole: form.requestedRole,
        fullName: form.fullName.trim(),
        phoneNumber: form.phoneNumber.trim() || null,
        email,
        password: form.password,
      }

      const response = await api.post('/api/users/register', payload)

      setSuccess(
        response.data?.message ||
          'Registration submitted. An institution administrator must approve your membership before you can sign in.',
      )

      setForm({
        institutionId: institutions.length > 0 ? String(institutions[0].id) : '',
        departmentId: '',
        requestedRole: 'RESEARCHER_STUDENT',
        fullName: '',
        phoneNumber: '',
        email: '',
        password: '',
        confirmPassword: '',
      })
    } catch (err) {
      const status = err.response?.status
      const data = err.response?.data
      const serverMessage = typeof data?.message === 'string' ? data.message : ''

      if (!err.response) {
        setError('Unable to connect to the server. Check that the backend is running.')
      } else if (
        status === 409 ||
        /already exists|duplicate email|email.*registered/i.test(serverMessage)
      ) {
        setError('An account with this email already exists.')
      } else if (status === 400) {
        setError(serverMessage || 'Please check your information and try again.')
      } else {
        setError(serverMessage || 'Registration failed. Please check your details and try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-card app-surface" style={{ maxWidth: '580px' }}>
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
            <label htmlFor="register-institution">Institution *</label>
            {loadingInstitutions ? (
              <p className="field-hint">Loading institutions…</p>
            ) : (
              <select
                id="register-institution"
                name="institutionId"
                value={form.institutionId}
                onChange={handleChange}
                required
                disabled={submitting}
              >
                <option value="" disabled>Select your institution</option>
                {institutions.map((inst) => (
                  <option key={inst.id} value={inst.id}>
                    {inst.name} (#{inst.id})
                  </option>
                ))}
              </select>
            )}
            <small className="field-hint">
              Select the college or university you are affiliated with.
            </small>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div className="form-field">
              <label htmlFor="register-department">Department</label>
              <select
                id="register-department"
                name="departmentId"
                value={form.departmentId}
                onChange={handleChange}
                disabled={submitting || loadingDepartments}
              >
                <option value="">General / None</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>
                    {dept.name}
                  </option>
                ))}
              </select>
              <small className="field-hint">
                {loadingDepartments ? 'Loading…' : departments.length === 0 ? 'No departments listed' : 'Choose department'}
              </small>
            </div>

            <div className="form-field">
              <label htmlFor="register-role">Requested Role *</label>
              <select
                id="register-role"
                name="requestedRole"
                value={form.requestedRole}
                onChange={handleChange}
                required
                disabled={submitting}
              >
                {APPLICANT_ROLES.map((role) => (
                  <option key={role.value} value={role.value}>
                    {role.label}
                  </option>
                ))}
              </select>
              <small className="field-hint">
                {APPLICANT_ROLES.find((r) => r.value === form.requestedRole)?.description}
              </small>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div className="form-field">
              <label htmlFor="register-fullname">Full Name *</label>
              <input
                id="register-fullname"
                name="fullName"
                type="text"
                maxLength={150}
                placeholder="e.g., Alex Johnson"
                value={form.fullName}
                onChange={handleChange}
                required
                disabled={submitting}
              />
            </div>

            <div className="form-field">
              <label htmlFor="register-phone">Phone Number</label>
              <input
                id="register-phone"
                name="phoneNumber"
                type="tel"
                maxLength={30}
                placeholder="e.g., +91 98765 43210"
                value={form.phoneNumber}
                onChange={handleChange}
                disabled={submitting}
              />
            </div>
          </div>

          <div className="form-field">
            <label htmlFor="register-email">Email address *</label>
            <input
              id="register-email"
              name="email"
              type="email"
              autoComplete="email"
              maxLength={180}
              placeholder="you@institution.edu"
              value={form.email}
              onChange={handleChange}
              required
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="register-password">Password *</label>
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
                className="password-toggle-button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>

          <div className="form-field">
            <label htmlFor="register-confirm">Confirm password *</label>
            <input
              id="register-confirm"
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
            {submitting ? 'Submitting registration…' : 'Register'}
          </button>
        </form>

        <div className="auth-footer" style={{ marginTop: '1.25rem' }}>
          <p>
            Represent an educational institution?{' '}
            <Link to="/register-institution">Register your institution here →</Link>
          </p>
          <p>
            Need platform-level oversight?{' '}
            <Link to="/apply-system-admin">Apply for System Administrator access →</Link>
          </p>
          <p>
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </section>
    </div>
  )
}
