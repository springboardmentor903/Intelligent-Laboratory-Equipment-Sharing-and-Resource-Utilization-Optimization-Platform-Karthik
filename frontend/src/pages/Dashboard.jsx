
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { getDashboardAccessPlan } from '../access/roleAccess.js'
import { BOOKING_REFRESH_EVENT } from '../access/bookingWorkflow.js'

const dashboardItems = [
  {
    icon: '🔬',
    title: 'Equipment',
    description: 'Browse laboratory equipment and check availability.',
    path: '/equipment',
    action: 'Browse equipment',
  },
  {
    icon: '📅',
    title: 'Bookings',
    description: 'Review equipment reservations and booking status.',
    path: '/bookings',
    action: 'View bookings',
  },
  {
    icon: '📊',
    title: 'Utilization',
    description: 'Review recorded equipment usage and utilization hours.',
    path: '/utilization',
    action: 'View utilization',
  },
  {
    icon: '🛠️',
    title: 'Maintenance',
    description: 'Track reported issues and equipment maintenance.',
    path: '/maintenance',
    action: 'View maintenance',
  },
]

function getCount(data) {
  return Array.isArray(data) ? data.length : 0
}

function getErrorMessage(error) {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    'Unable to retrieve dashboard information.'
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const accessPlan = useMemo(
    () => getDashboardAccessPlan(user?.roles, user?.id),
    [user?.id, user?.roles],
  )

  const [backendStatus, setBackendStatus] = useState('Checking backend…')
  const [backendStatusType, setBackendStatusType] = useState('checking')

  const [stats, setStats] = useState({
    equipment: null,
    bookings: null,
    utilization: null,
    maintenance: null,
  })

  const [loadingStats, setLoadingStats] = useState(true)
  const [statsError, setStatsError] = useState('')

  const loadDashboard = useCallback(async () => {
    setLoadingStats(true)
    setStatsError('')

    try {
      if (!accessPlan) {
        setStatsError('Your session role is invalid. Sign in again to continue.')
        setStats({ equipment: null, bookings: null, utilization: null, maintenance: null })
        return
      }

      const requests = [
        { name: 'Equipment', promise: api.get('/api/equipment') },
        { name: 'Bookings', promise: accessPlan.bookingsUrl
          ? api.get(accessPlan.bookingsUrl)
          : Promise.reject(new Error('Your signed-in user ID is unavailable.')) },
        { name: 'Utilization', promise: api.get('/api/utilization/summary') },
      ]
      if (accessPlan.maintenanceSummaryUrl) {
        requests.push({
          name: 'Maintenance',
          promise: api.get(accessPlan.maintenanceSummaryUrl),
        })
      }

      const results = await Promise.allSettled(requests.map(({ promise }) => promise))
      const resultsByName = Object.fromEntries(
        requests.map(({ name }, index) => [name, results[index]]),
      )
      const equipmentResult = resultsByName.Equipment
      const bookingsResult = resultsByName.Bookings
      const utilizationResult = resultsByName.Utilization
      const maintenanceResult = resultsByName.Maintenance

      setStats({
        equipment:
          equipmentResult.status === 'fulfilled'
            ? getCount(equipmentResult.value.data)
            : null,

        bookings:
          bookingsResult.status === 'fulfilled'
            ? getCount(bookingsResult.value.data)
            : null,

        utilization:
          utilizationResult.status === 'fulfilled'
            ? utilizationResult.value.data
            : null,

        maintenance: maintenanceResult?.status === 'fulfilled'
          ? maintenanceResult.value.data
          : null,
      })

      const failedSections = results
        .map((result, index) => {
          if (result.status !== 'rejected') return null

          return `${requests[index].name}: ${getErrorMessage(result.reason)}`
        })
        .filter(Boolean)

      if (failedSections.length > 0) {
        setStatsError(
          `Some statistics could not be loaded. ${failedSections.join(' | ')}`,
        )
      }
    } catch (error) {
      setStatsError(getErrorMessage(error))
    } finally {
      setLoadingStats(false)
    }
  }, [accessPlan])

  useEffect(() => {
    let active = true
    const handleBookingRefresh = () => loadDashboard()
    window.addEventListener(BOOKING_REFRESH_EVENT, handleBookingRefresh)

    api
      .get('/api/health')
      .then((response) => {
        if (!active) return

        if (response.data?.status === 'UP') {
          setBackendStatus('Connected')
          setBackendStatusType('success')
        } else {
          setBackendStatus('Unexpected response')
          setBackendStatusType('warning')
        }
      })
      .catch(() => {
        if (!active) return

        setBackendStatus('Backend not reachable')
        setBackendStatusType('error')
      })

    // Defer the asynchronous stateful loader until after this effect commits.
    void Promise.resolve().then(loadDashboard)

    return () => {
      active = false
      window.removeEventListener(BOOKING_REFRESH_EVENT, handleBookingRefresh)
    }
  }, [loadDashboard])

  const cards = [
    {
      label: 'Equipment',
      value: stats.equipment,
      detail: 'Equipment records',
      icon: '🔬',
      path: '/equipment',
    },
    {
      label: accessPlan?.bookingScope === 'platform'
        ? 'Platform bookings'
        : accessPlan?.bookingScope === 'institution'
          ? 'Institution bookings'
          : 'My bookings',
      value: stats.bookings,
      detail: accessPlan?.bookingScope === 'platform'
        ? 'Platform-wide booking records'
        : accessPlan?.bookingScope === 'institution'
          ? 'Institution-scoped booking records'
          : 'Your booking records',
      icon: '📅',
      path: '/bookings',
    },
    {
      label: 'Usage hours',
      value: stats.utilization?.totalUsageHours ?? null,
      detail: 'Recorded utilization',
      icon: '◷',
      path: '/utilization',
    },
    {
      label: 'Maintenance',
      value: stats.maintenance?.totalRecords ?? null,
      detail: accessPlan?.maintenanceSummaryUrl
        ? accessPlan.bookingScope === 'platform'
          ? 'Platform-wide maintenance summary'
          : 'Maintenance summary for your institution'
        : 'Summary not available; view your reported requests in Maintenance',
      icon: '🛠️',
      path: '/maintenance',
    },
  ]

  return (
    <div className="dashboard-page">
      <section className="dashboard-welcome">
        <div>
          <span className="eyebrow">WORKSPACE OVERVIEW</span>
          <h1>Dashboard</h1>
          <p>
            Welcome{user?.email ? `, ${user.email}` : ''}. Manage your
            laboratory resources from one place.
          </p>
        </div>

        <div className={`backend-status status-${backendStatusType}`}>
          <span className="status-indicator" />
          <div>
            <small>Backend status</small>
            <strong>{backendStatus}</strong>
          </div>
        </div>
      </section>

      <section className="dashboard-summary">
        <article className="summary-card">
          <div className="summary-icon">🏢</div>
          <div>
            <span>Institution ID</span>
            <strong>{user?.institutionId ?? '—'}</strong>
          </div>
        </article>

        <article className="summary-card">
          <div className="summary-icon">👤</div>
          <div>
            <span>Account</span>
            <strong>
              {user?.email ? 'Signed in' : 'Session unavailable'}
            </strong>
          </div>
        </article>

        <article className="summary-card">
          <div className="summary-icon">🔗</div>
          <div>
            <span>API connection</span>
            <strong>{backendStatus}</strong>
          </div>
        </article>
      </section>

      <section className="dashboard-section">
        <div className="dashboard-section-heading">
          <div>
            <span className="eyebrow">LIVE DATA</span>
            <h2>Resource summary</h2>
          </div>

          <button
            type="button"
            className="secondary-button"
            onClick={loadDashboard}
            disabled={loadingStats}
          >
            {loadingStats ? 'Loading…' : '↻ Refresh statistics'}
          </button>
        </div>

        {statsError && (
          <div className="auth-alert" role="alert">
            {statsError}
          </div>
        )}

        <div className="dashboard-live-stats">
          {cards.map((card) => (
            <Link
              to={card.path}
              className="dashboard-live-stat"
              key={card.label}
            >
              <div className="dashboard-live-stat-top">
                <span aria-hidden="true">{card.icon}</span>
                <span>{card.label}</span>
              </div>

              <strong>
                {loadingStats
                  ? '…'
                  : card.value === null || card.value === undefined
                    ? 'Unavailable'
                    : card.value}
              </strong>

              <span className="dashboard-live-stat-detail">
                {card.detail}
              </span>
            </Link>
          ))}
        </div>
      </section>

      <section className="dashboard-section">
        <div className="dashboard-section-heading">
          <div>
            <span className="eyebrow">RESOURCE MANAGEMENT</span>
            <h2>Platform modules</h2>
          </div>
          <p>Choose a module to continue.</p>
        </div>

        <div className="dashboard-module-grid">
          {dashboardItems.map((item) => (
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

      <section className="dashboard-notice">
        <span className="notice-icon" aria-hidden="true">
          ℹ️
        </span>
        <div>
          <strong>About these statistics</strong>
          <p>
            Counts and usage hours come from your backend responses.
            Unavailable values indicate an API request failed; they are not
            treated as zero.
          </p>
        </div>
      </section>
    </div>
  )
}
