import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api/axiosInstance'
import { getRoleGuidance, getRoleHomeItems, ROLE_LABELS } from '../access/homeAccess.js'

const publicFeatures = [
  {
    icon: '🔬',
    title: 'Equipment Management',
    description:
      'Organize laboratory equipment, track availability, and manage resources in one place.',
  },
  {
    icon: '📅',
    title: 'Smart Bookings',
    description:
      'Request equipment reservations and manage booking schedules with fewer conflicts.',
  },
  {
    icon: '📊',
    title: 'Utilization Analytics',
    description:
      'Understand equipment usage and identify opportunities to improve resource utilization.',
  },
  {
    icon: '🛠️',
    title: 'Maintenance Tracking',
    description:
      'Record equipment issues and follow maintenance activities to support laboratory operations.',
  },
]

export default function Home() {
  const { isAuthenticated, loading, user } = useAuth()

  if (loading) {
    return (
      <div className="route-loading" role="status">
        Checking your session…
      </div>
    )
  }

  if (isAuthenticated && user) {
    return <AuthenticatedHome user={user} />
  }

  return <PublicHome />
}

function AuthenticatedHome({ user }) {
  const roles = Array.isArray(user?.roles) ? user.roles : []
  const roleLabels = roles.map((r) => ROLE_LABELS[r] || r)
  const homeItems = getRoleHomeItems(roles)
  const guidance = getRoleGuidance(roles)
  const isSystemAdmin = roles.includes('SYSTEM_ADMIN')
  const displayName = user?.fullName || (user?.email ? user.email.split('@')[0] : 'User')

  const [institutionName, setInstitutionName] = useState('')

  useEffect(() => {
    let active = true
    if (!isSystemAdmin && user?.institutionId) {
      api.get(`/api/institutions/${user.institutionId}`)
        .then(({ data }) => {
          if (active && data?.name) {
            setInstitutionName(data.name)
          }
        })
        .catch(() => {})
    }
    return () => { active = false }
  }, [isSystemAdmin, user?.institutionId])

  const subtitle = isSystemAdmin
    ? 'Platform-wide access · Your session is authenticated and active.'
    : institutionName
      ? `Connected to ${institutionName} · Your session is authenticated and active.`
      : user?.institutionId
        ? `Connected to Institution #${user.institutionId} · Your session is authenticated and active.`
        : 'Your session is authenticated and active.'

  return (
    <div className="home-page authenticated-home">
      <section className="dashboard-welcome">
        <div>
          <span className="eyebrow">LABORATORY WORKSPACE</span>
          <h1>Welcome back, {displayName}</h1>
          <p>{subtitle}</p>
          {roleLabels.length > 0 && (
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '12px' }}>
              {roleLabels.map((label) => (
                <span key={label} className="auth-role-badge">
                  {label}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="hero-actions" style={{ margin: 0, display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <Link to="/dashboard" className="button button-primary">
            Open Dashboard <span aria-hidden="true">→</span>
          </Link>
          <Link to="/profile" className="button button-secondary">
            View Profile
          </Link>
        </div>
      </section>

      <section className="dashboard-section">
        <div className="dashboard-section-heading">
          <div>
            <span className="eyebrow">WORKSPACE MODULES</span>
            <h2>Quick access</h2>
          </div>
        </div>

        <div className="dashboard-module-grid">
          {homeItems.map((item) => (
            <article className="dashboard-module-card" key={item.title}>
              <div className="module-icon" aria-hidden="true">
                {item.icon}
              </div>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
              <Link to={item.path} className="module-link">
                {item.action} <span aria-hidden="true">→</span>
              </Link>
            </article>
          ))}
        </div>
      </section>

      {guidance && (
        <section className="dashboard-notice">
          <span className="notice-icon" aria-hidden="true">
            🛡️
          </span>
          <div>
            <strong>Role & Permissions Overview</strong>
            <p>{guidance}</p>
          </div>
        </section>
      )}
    </div>
  )
}

function PublicHome() {
  return (
    <div className="home-page">
      <section className="hero-section">
        <div className="hero-content">
          <span className="eyebrow">
            LABORATORY RESOURCE MANAGEMENT
          </span>

          <h1>
            Make every laboratory
            <span className="hero-highlight"> resource count.</span>
          </h1>

          <p className="hero-description">
            A centralized platform to manage laboratory equipment,
            coordinate bookings, monitor utilization, and organize
            maintenance activities.
          </p>

          <div className="hero-actions">
            <Link to="/register" className="button button-primary">
              Get started <span aria-hidden="true">→</span>
            </Link>

            <Link to="/login" className="button button-secondary">
              Sign in
            </Link>
          </div>

          <div className="hero-note">
            <span className="status-dot" />
            One platform for smarter laboratory operations
          </div>
        </div>

        <div className="hero-visual" aria-label="Laboratory resource overview">
          <div className="visual-orbit orbit-one" />
          <div className="visual-orbit orbit-two" />

          <div className="visual-center">
            <span aria-hidden="true">🔬</span>
            <strong>Lab Resources</strong>
            <small>Connected. Organized. Optimized.</small>
          </div>

          <div className="floating-card floating-card-top">
            <span className="floating-icon">📅</span>
            <div>
              <strong>Equipment booking</strong>
              <small>Scheduling made simple</small>
            </div>
          </div>

          <div className="floating-card floating-card-bottom">
            <span className="floating-icon">📈</span>
            <div>
              <strong>Resource utilization</strong>
              <small>Usage insights in one place</small>
            </div>
          </div>
        </div>
      </section>

      <section className="features-section">
        <div className="section-heading">
          <span className="eyebrow">PLATFORM CAPABILITIES</span>
          <h2>Everything your lab needs to stay organized</h2>
          <p>
            Essential tools to support equipment sharing and laboratory
            resource management.
          </p>
        </div>

        <div className="feature-grid">
          {publicFeatures.map((feature) => (
            <article className="feature-card" key={feature.title}>
              <div className="feature-icon" aria-hidden="true">
                {feature.icon}
              </div>
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="home-cta">
        <div>
          <h2>Ready to organize your laboratory resources?</h2>
          <p>
            Sign in to continue or create an account to get started.
          </p>
        </div>

        <Link to="/register" className="button button-light">
          Create an account <span aria-hidden="true">→</span>
        </Link>
      </section>
    </div>
  )
}
