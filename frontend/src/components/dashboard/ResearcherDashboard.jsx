import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../../api/axiosInstance'
import { useAuth } from '../../context/AuthContext'

export default function ResearcherDashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadData = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/dashboard/researcher')
      setData(res.data)
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load researcher dashboard.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  if (loading) {
    return (
      <div style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--text-muted)' }}>
        <div style={{ fontSize: '1.5rem', marginBottom: 8 }}>🔄</div>
        Loading your personal researcher dashboard…
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: 24 }}>
        <div style={{ padding: 16, background: '#fee2e2', color: '#991b1b', borderRadius: 8, marginBottom: 16 }}>
          {error}
        </div>
        <button type="button" onClick={loadData} className="primary-button" style={{ padding: '8px 16px', cursor: 'pointer' }}>
          Retry
        </button>
      </div>
    )
  }

  const {
    userFullName,
    userEmail,
    nextBookings = [],
    availabilityOverview = {},
    usageSummary = {},
    waitlist = { waitingCount: 0, offeredCount: 0, activeOffers: [] },
    unreadNotificationCount = 0,
    latestNotifications = [],
    recommendations = [],
  } = data || {}

  const activeOffers = waitlist?.activeOffers || []

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* 1. Header Greeting */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: '24px 28px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 16
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <span style={{
              background: 'var(--primary-light, #ccfbf1)',
              color: 'var(--primary, #0f766e)',
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '2px 8px',
              borderRadius: 6,
              letterSpacing: '0.05em'
            }}>
              RESEARCHER / STUDENT
            </span>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Personal Workspace</span>
          </div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: 0, color: 'var(--text)' }}>
            Welcome, {userFullName || user?.fullName || userEmail || user?.email || 'Researcher'}!
          </h1>
          <p style={{ margin: '6px 0 0', color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            Track your upcoming lab sessions, equipment availability, and historical usage in one place.
          </p>
        </div>

        <button
          type="button"
          onClick={loadData}
          style={{
            background: 'var(--surface-secondary, #f1f5f9)',
            border: '1px solid var(--border)',
            padding: '8px 14px',
            borderRadius: 8,
            cursor: 'pointer',
            fontSize: '0.85rem',
            fontWeight: 600,
            color: 'var(--text)'
          }}
        >
          ↻ Refresh Dashboard
        </button>
      </section>

      {/* 2. Active Waitlist Offer Banner (If any) */}
      {activeOffers.length > 0 && (
        <section style={{
          background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
          border: '1px solid #f59e0b',
          borderRadius: 12,
          padding: '16px 20px',
          display: 'flex',
          flexDirection: 'column',
          gap: 10
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: '1.4rem' }}>🎉</span>
            <strong style={{ color: '#92400e', fontSize: '1.05rem' }}>
              Equipment Reservation Offer Available!
            </strong>
          </div>
          {activeOffers.map((offer) => (
            <div
              key={offer.waitlistId}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 12,
                background: 'rgba(255, 255, 255, 0.7)',
                padding: '10px 14px',
                borderRadius: 8
              }}
            >
              <div>
                <strong style={{ color: '#78350f' }}>{offer.equipmentName}</strong>
                <div style={{ fontSize: '0.85rem', color: '#92400e' }}>
                  Offered Window: {offer.requestedStartTime ? new Date(offer.requestedStartTime).toLocaleString() : 'N/A'} - {offer.requestedEndTime ? new Date(offer.requestedEndTime).toLocaleString() : 'N/A'}
                  {offer.expiresAt && ` (Offer expires: ${new Date(offer.expiresAt).toLocaleTimeString()})`}
                </div>
              </div>
              <Link
                to="/bookings"
                style={{
                  background: '#d97706',
                  color: '#ffffff',
                  padding: '6px 14px',
                  borderRadius: 6,
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  textDecoration: 'none'
                }}
              >
                Claim Booking →
              </Link>
            </div>
          ))}
        </section>
      )}

      {/* 3. 90-Day Usage Summary Cards */}
      <section>
        <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 12px', color: 'var(--text)' }}>
          Your 90-Day Usage Summary
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 600 }}>COMPLETED SESSIONS</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--text)', marginTop: 4 }}>
              {usageSummary.completedBookings ?? 0}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
              {usageSummary.totalBookings ?? 0} total bookings ({usageSummary.cancelledBookings ?? 0} cancelled)
            </div>
          </div>

          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 600 }}>HOURS USED</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#0284c7', marginTop: 4 }}>
              {usageSummary.hoursUsed != null ? Number(usageSummary.hoursUsed).toFixed(1) : '0.0'} hrs
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
              Recorded lab instrument runtime
            </div>
          </div>

          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 600 }}>NO-SHOWS</div>
            <div style={{
              fontSize: '1.8rem',
              fontWeight: 800,
              color: (usageSummary.noShows || 0) > 0 ? '#dc2626' : '#16a34a',
              marginTop: 4
            }}>
              {usageSummary.noShows ?? 0}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
              {(usageSummary.noShows || 0) > 0 ? 'Missed scheduled slots' : 'Flawless attendance record'}
            </div>
          </div>

          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 600 }}>ESTIMATED USAGE COST</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#0f766e', marginTop: 4 }}>
              ₹{Number(usageSummary.usageCost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
              Cumulative internal usage charges
            </div>
          </div>
        </div>
      </section>

      {/* 4. Next 5 Bookings & Availability Overview Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 20 }}>
        {/* Next 5 Bookings */}
        <section style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 14
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              📅 Next Bookings
            </h2>
            <Link to="/bookings" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
              All Bookings →
            </Link>
          </div>

          {nextBookings.length === 0 ? (
            <div style={{ padding: '24px 12px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              No upcoming bookings scheduled.
              <div style={{ marginTop: 8 }}>
                <Link to="/equipment" className="primary-button" style={{ display: 'inline-block', padding: '6px 14px', textDecoration: 'none', fontSize: '0.85rem' }}>
                  Browse Equipment & Reserve
                </Link>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {nextBookings.map((b) => (
                <div
                  key={b.bookingId}
                  style={{
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    padding: '10px 14px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    background: 'var(--surface-secondary, #fafafa)'
                  }}
                >
                  <div>
                    <strong style={{ fontSize: '0.95rem', color: 'var(--text)' }}>{b.equipmentName}</strong>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
                      {b.startTime ? new Date(b.startTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' }) : 'N/A'} - {b.endTime ? new Date(b.endTime).toLocaleTimeString([], { timeStyle: 'short' }) : 'N/A'}
                    </div>
                  </div>
                  <span style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    padding: '3px 8px',
                    borderRadius: 6,
                    background: b.status === 'CONFIRMED' ? '#dcfce7' : b.status === 'IN_USE' ? '#e0f2fe' : '#fef3c7',
                    color: b.status === 'CONFIRMED' ? '#15803d' : b.status === 'IN_USE' ? '#0369a1' : '#b45309'
                  }}>
                    {b.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Equipment Availability Overview */}
        <section style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 14
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              🔬 Equipment Availability
            </h2>
            <Link to="/equipment" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
              Open Catalog →
            </Link>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <Link
              to="/equipment?status=AVAILABLE"
              style={{
                textDecoration: 'none',
                background: '#f0fdf4',
                border: '1px solid #bbf7d0',
                borderRadius: 8,
                padding: 14,
                display: 'flex',
                flexDirection: 'column',
                gap: 4
              }}
            >
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#166534' }}>AVAILABLE NOW</span>
              <strong style={{ fontSize: '1.6rem', color: '#15803d' }}>
                {availabilityOverview['AVAILABLE'] ?? 0}
              </strong>
            </Link>

            <Link
              to="/equipment?status=IN_USE"
              style={{
                textDecoration: 'none',
                background: '#f0f9ff',
                border: '1px solid #bae6fd',
                borderRadius: 8,
                padding: 14,
                display: 'flex',
                flexDirection: 'column',
                gap: 4
              }}
            >
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#075985' }}>CURRENTLY IN USE</span>
              <strong style={{ fontSize: '1.6rem', color: '#0284c7' }}>
                {availabilityOverview['IN_USE'] ?? 0}
              </strong>
            </Link>

            <Link
              to="/equipment?status=UNDER_MAINTENANCE"
              style={{
                textDecoration: 'none',
                background: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: 8,
                padding: 14,
                display: 'flex',
                flexDirection: 'column',
                gap: 4
              }}
            >
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#92400e' }}>MAINTENANCE</span>
              <strong style={{ fontSize: '1.6rem', color: '#d97706' }}>
                {availabilityOverview['UNDER_MAINTENANCE'] ?? 0}
              </strong>
            </Link>

            <div style={{
              background: 'var(--surface-secondary, #f8fafc)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              padding: 14,
              display: 'flex',
              flexDirection: 'column',
              gap: 4
            }}>
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>WAITLIST QUEUE</span>
              <strong style={{ fontSize: '1.6rem', color: 'var(--text)' }}>
                {waitlist?.waitingCount ?? 0} waiting
              </strong>
            </div>
          </div>

          {/* Latest Notifications Box */}
          <div style={{ marginTop: 6, borderTop: '1px solid var(--border)', paddingTop: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: '0.9rem' }}>🔔</span>
                <strong style={{ fontSize: '0.85rem', color: 'var(--text)' }}>Latest Notifications</strong>
                {unreadNotificationCount > 0 && (
                  <span style={{
                    background: '#ef4444',
                    color: '#ffffff',
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    borderRadius: 10,
                    padding: '1px 6px'
                  }}>
                    {unreadNotificationCount} unread
                  </span>
                )}
              </div>
              <Link to="/notifications" style={{ fontSize: '0.8rem', color: 'var(--accent-ocean)' }}>View All</Link>
            </div>

            {latestNotifications.length === 0 ? (
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No recent notifications.</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {latestNotifications.slice(0, 3).map((n) => (
                  <div key={n.id} style={{ fontSize: '0.8rem', color: 'var(--text)', background: 'var(--surface-secondary)', padding: '6px 10px', borderRadius: 6 }}>
                    <div style={{ fontWeight: 600 }}>{n.title}</div>
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>{n.message}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>

      {/* 5. Recommended Equipment For You */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: 22
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              💡 Recommended Equipment For You
            </h2>
            <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Curated based on your 180-day booking preferences and popular institution resources
            </p>
          </div>
          <Link to="/equipment" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
            Catalog →
          </Link>
        </div>

        {recommendations.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            No recommendations available right now. Browse our full catalog to discover equipment.
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 14 }}>
            {recommendations.map((rec) => (
              <div
                key={rec.equipmentId}
                style={{
                  border: '1px solid var(--border)',
                  borderRadius: 10,
                  padding: 14,
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: 10,
                  background: 'var(--surface-secondary, #fafafa)'
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 6 }}>
                    <span style={{ fontSize: '1.3rem' }}>🔬</span>
                    <span style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      padding: '2px 6px',
                      borderRadius: 4,
                      background: rec.status === 'AVAILABLE' ? '#dcfce7' : '#fef3c7',
                      color: rec.status === 'AVAILABLE' ? '#15803d' : '#b45309'
                    }}>
                      {rec.status}
                    </span>
                  </div>
                  <strong style={{ display: 'block', marginTop: 8, fontSize: '0.95rem', color: 'var(--text)' }}>
                    {rec.equipmentName}
                  </strong>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
                    {rec.category || 'General Equipment'}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#0284c7', marginTop: 4, fontStyle: 'italic' }}>
                    {rec.reason}
                  </div>
                </div>

                <div style={{ borderTop: '1px solid var(--border)', paddingTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#0f766e' }}>
                    ₹{rec.hourlyRate != null ? Number(rec.hourlyRate).toFixed(2) : '0.00'}/hr
                  </span>
                  <Link
                    to="/equipment"
                    style={{
                      background: 'var(--primary, #0f766e)',
                      color: '#ffffff',
                      padding: '4px 10px',
                      borderRadius: 6,
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      textDecoration: 'none'
                    }}
                  >
                    Book Now
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
