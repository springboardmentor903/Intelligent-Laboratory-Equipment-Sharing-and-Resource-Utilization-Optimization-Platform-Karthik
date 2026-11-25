import { useEffect, useState, useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../../api/axiosInstance'
import { useAuth } from '../../context/AuthContext'

export default function ManagerDashboard() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()

  const periodDays = Number(searchParams.get('days') || '30')
  const departmentIdParam = searchParams.get('departmentId') || ''

  const [data, setData] = useState(null)
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const isLabManager = user?.roles?.includes('LAB_MANAGER') || user?.roles?.includes('SYSTEM_ADMIN') || user?.roles?.includes('INSTITUTION_ADMIN')
  const isDeptHead = user?.roles?.includes('DEPARTMENT_HEAD')

  const fetchDepartments = useCallback(async () => {
    try {
      const res = await api.get('/api/departments')
      setDepartments(Array.isArray(res.data) ? res.data : [])
    } catch {
      // Non-critical if departments fails
    }
  }, [])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams()
      query.append('days', periodDays)
      if (departmentIdParam) {
        query.append('departmentId', departmentIdParam)
      }

      const res = await api.get(`/api/dashboard/manager?${query.toString()}`)
      setData(res.data)
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load manager dashboard.')
    } finally {
      setLoading(false)
    }
  }, [periodDays, departmentIdParam])

  useEffect(() => {
    if (isLabManager) {
      fetchDepartments()
    }
  }, [isLabManager, fetchDepartments])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handlePeriodChange = (newDays) => {
    const next = new URLSearchParams(searchParams)
    next.set('days', String(newDays))
    setSearchParams(next)
  }

  const handleDepartmentChange = (newDeptId) => {
    const next = new URLSearchParams(searchParams)
    if (newDeptId) {
      next.set('departmentId', String(newDeptId))
    } else {
      next.delete('departmentId')
    }
    setSearchParams(next)
  }

  if (loading && !data) {
    return (
      <div style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--text-muted)' }}>
        <div style={{ fontSize: '1.5rem', marginBottom: 8 }}>🔄</div>
        Loading operational manager dashboard…
      </div>
    )
  }

  if (error && !data) {
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
    scope = 'INSTITUTION',
    departmentName,
    utilizationAgainstTarget = { targetUtilizationPercentage: 70.0, currentUtilizationPercentage: 0.0, bookedHours: 0.0, totalAvailableHours: 0.0, status: 'RED' },
    hourlyBookingHeatmap = [],
    bookingAdoptionPercentage = 0.0,
    noShowRatePercentage = 0.0,
    highDemandEquipment = [],
    openWorkOrdersBySeverity = {},
    equipmentUnderMaintenance = [],
    preventiveScheduleAlerts = [],
    calibrationOverview = { VALID: 0, DUE_SOON: 0, EXPIRED: 0, NONE: 0 },
    roleSpecificApprovals = { pendingBookingApprovals: 0, pendingSharingRequests: 0 },
  } = data || {}

  const currentUtil = utilizationAgainstTarget.currentUtilizationPercentage || 0.0
  const targetUtil = utilizationAgainstTarget.targetUtilizationPercentage || 70.0
  const utilStatus = currentUtil >= targetUtil ? 'GREEN' : currentUtil >= 40.0 ? 'AMBER' : 'RED'
  const utilColor = utilStatus === 'GREEN' ? '#16a34a' : utilStatus === 'AMBER' ? '#d97706' : '#dc2626'
  const utilBg = utilStatus === 'GREEN' ? '#dcfce7' : utilStatus === 'AMBER' ? '#fef3c7' : '#fee2e2'

  return (
    <div style={{ maxWidth: 1250, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* 1. Header with Period Selector & Scope Filters */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: '20px 24px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 16
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <span style={{
              background: '#e0e7ff',
              color: '#4338ca',
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '2px 8px',
              borderRadius: 6
            }}>
              {isDeptHead ? 'DEPARTMENT HEAD DASHBOARD' : 'LAB MANAGER DASHBOARD'}
            </span>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              Scope: <strong>{scope === 'DEPARTMENT' ? (departmentName || 'Your Department') : 'Institution-wide'}</strong>
            </span>
          </div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, margin: 0, color: 'var(--text)' }}>
            Operations & Resource Utilization
          </h1>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          {/* Department filter (Only for Lab Manager / Admin) */}
          {isLabManager && departments.length > 0 && (
            <div>
              <select
                value={departmentIdParam}
                onChange={(e) => handleDepartmentChange(e.target.value)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  background: 'var(--surface)',
                  color: 'var(--text)',
                  fontSize: '0.85rem'
                }}
              >
                <option value="">All Departments</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            </div>
          )}

          {/* Period selector: 7, 30, 90 */}
          <div style={{ display: 'flex', background: 'var(--surface-secondary)', border: '1px solid var(--border)', borderRadius: 8, padding: 3 }}>
            {[7, 30, 90].map((days) => (
              <button
                key={days}
                type="button"
                onClick={() => handlePeriodChange(days)}
                style={{
                  border: 'none',
                  padding: '6px 12px',
                  borderRadius: 6,
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  background: periodDays === days ? 'var(--primary, #0f766e)' : 'transparent',
                  color: periodDays === days ? '#ffffff' : 'var(--text-muted)',
                }}
              >
                {days} Days
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={loadData}
            style={{
              background: 'var(--surface-secondary)',
              border: '1px solid var(--border)',
              padding: '6px 12px',
              borderRadius: 6,
              fontSize: '0.85rem',
              cursor: 'pointer',
              color: 'var(--text)'
            }}
          >
            ↻
          </button>
        </div>
      </section>

      {/* 2. Key Metrics & Approvals Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 14 }}>
        {/* Utilization vs Target Card */}
        <div style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 10,
          padding: 18,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>UTILIZATION RATE</span>
            <span style={{ fontSize: '0.75rem', fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: utilBg, color: utilColor }}>
              Target: {targetUtil.toFixed(0)}%
            </span>
          </div>
          <div style={{ margin: '10px 0' }}>
            <div style={{ fontSize: '2.1rem', fontWeight: 800, color: utilColor }}>
              {currentUtil.toFixed(1)}%
            </div>
            {/* Visual progress bar */}
            <div style={{ height: 8, background: '#e2e8f0', borderRadius: 4, overflow: 'hidden', position: 'relative', marginTop: 8 }}>
              <div style={{ width: `${Math.min(100, currentUtil)}%`, height: '100%', background: utilColor, transition: 'width 0.4s ease' }} />
              <div style={{ position: 'absolute', top: 0, left: `${targetUtil}%`, width: 2, height: '100%', background: '#0f172a' }} title="Target line" />
            </div>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {utilizationAgainstTarget.bookedHours?.toFixed(1) || '0.0'}h booked of {utilizationAgainstTarget.totalAvailableHours?.toFixed(1) || '0.0'}h operational capacity
          </div>
        </div>

        {/* Booking Adoption % */}
        <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
          <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>BOOKING ADOPTION</div>
          <div style={{ fontSize: '2.1rem', fontWeight: 800, color: '#0284c7', marginTop: 8 }}>
            {Number(bookingAdoptionPercentage).toFixed(1)}%
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 8 }}>
            Active lab user adoption across departments
          </div>
        </div>

        {/* No-Show Rate */}
        <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>NO-SHOW RATE</span>
            {noShowRatePercentage > 5 && (
              <span style={{ fontSize: '0.7rem', fontWeight: 700, padding: '2px 6px', borderRadius: 4, background: '#fee2e2', color: '#991b1b' }}>
                High Miss Rate
              </span>
            )}
          </div>
          <div style={{
            fontSize: '2.1rem',
            fontWeight: 800,
            color: noShowRatePercentage > 5 ? '#dc2626' : '#16a34a',
            marginTop: 8
          }}>
            {Number(noShowRatePercentage).toFixed(1)}%
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 8 }}>
            Percentage of booked reservations not attended
          </div>
        </div>

        {/* Role-Specific Approvals Card */}
        <div style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 10,
          padding: 18,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>ACTION REQUIRED</div>
            <div style={{ fontSize: '2.1rem', fontWeight: 800, color: 'var(--text)', marginTop: 8 }}>
              {isDeptHead ? (roleSpecificApprovals.pendingSharingRequests || 0) : (roleSpecificApprovals.pendingBookingApprovals || 0)}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>
              {isDeptHead ? 'Pending Department Sharing Requests' : 'Pending Booking Approvals'}
            </div>
          </div>
          <Link
            to={isDeptHead ? '/sharing' : '/bookings'}
            style={{
              marginTop: 10,
              fontSize: '0.85rem',
              fontWeight: 600,
              color: 'var(--accent-ocean)',
              textDecoration: 'none'
            }}
          >
            Review Requests →
          </Link>
        </div>
      </div>

      {/* 3. 7-Day Hourly Booking Heatmap */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: 22
      }}>
        <div style={{ marginBottom: 14 }}>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
            🔥 7-Day Hourly Booking Heatmap
          </h2>
          <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Density of equipment usage across days of the week and hours of the day
          </p>
        </div>

        {/* Heatmap Grid */}
        <div style={{ overflowX: 'auto', paddingBottom: 8 }}>
          <div style={{ minWidth: 640 }}>
            {/* Hour header */}
            <div style={{ display: 'grid', gridTemplateColumns: '60px repeat(12, 1fr)', gap: 4, marginBottom: 4 }}>
              <div />
              {['8 AM', '9 AM', '10 AM', '11 AM', '12 PM', '1 PM', '2 PM', '3 PM', '4 PM', '5 PM', '6 PM', '7 PM'].map((h) => (
                <div key={h} style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textAlign: 'center', fontWeight: 600 }}>
                  {h}
                </div>
              ))}
            </div>

            {/* Days rows */}
            {['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((day) => {
              const dayCells = hourlyBookingHeatmap.filter((c) => c.dayOfWeek?.toUpperCase() === day)
              return (
                <div key={day} style={{ display: 'grid', gridTemplateColumns: '60px repeat(12, 1fr)', gap: 4, marginBottom: 4 }}>
                  <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text)', display: 'flex', alignItems: 'center' }}>
                    {day.slice(0, 3)}
                  </div>
                  {[8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19].map((hour) => {
                    const match = dayCells.find((c) => c.hourOfDay === hour)
                    const count = match ? match.bookingCount : 0
                    const intensity = Math.min(1, count / 5.0)
                    const bg = count === 0
                      ? 'var(--surface-secondary, #f1f5f9)'
                      : `rgba(15, 118, 110, ${Math.max(0.2, intensity)})`
                    return (
                      <div
                        key={hour}
                        title={`${day} ${hour}:00 - ${count} bookings`}
                        style={{
                          height: 26,
                          borderRadius: 4,
                          background: bg,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '0.7rem',
                          fontWeight: 700,
                          color: count > 2 ? '#ffffff' : count > 0 ? '#0f766e' : 'transparent',
                          cursor: 'default',
                          transition: 'transform 0.1s'
                        }}
                      >
                        {count > 0 ? count : ''}
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* 4. High-Demand Equipment & Calibration Overview Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 20 }}>
        {/* Top High-Demand Equipment */}
        <section style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 12
        }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
            📈 High-Demand Equipment
          </h2>
          {highDemandEquipment.length === 0 ? (
            <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', padding: '16px 0' }}>
              No high-demand equipment recorded in the selected period.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {highDemandEquipment.slice(0, 5).map((eq, i) => (
                <div
                  key={eq.equipmentId}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: 'var(--surface-secondary, #fafafa)',
                    border: '1px solid var(--border)'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{
                      fontWeight: 800,
                      fontSize: '0.85rem',
                      color: i === 0 ? '#d97706' : 'var(--text-muted)',
                      width: 16
                    }}>
                      #{i + 1}
                    </span>
                    <div>
                      <strong style={{ fontSize: '0.9rem', color: 'var(--text)' }}>{eq.equipmentName}</strong>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{eq.category || 'General'}</div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.85rem', color: '#0284c7' }}>
                      {eq.totalBookings} bookings
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {eq.totalHoursUsed?.toFixed(1) || '0.0'} hrs
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Calibration Overview */}
        <section style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 12
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              🎯 Calibration Overview
            </h2>
            <Link to="/calibration" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
              Details →
            </Link>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 }}>
            <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 8, padding: 12 }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#166534' }}>VALID</span>
              <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#15803d', marginTop: 2 }}>
                {calibrationOverview.VALID ?? 0}
              </div>
            </div>

            <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: 8, padding: 12 }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#92400e' }}>DUE SOON (&lt;30d)</span>
              <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#d97706', marginTop: 2 }}>
                {calibrationOverview.DUE_SOON ?? 0}
              </div>
            </div>

            <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, padding: 12 }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#991b1b' }}>EXPIRED</span>
              <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#dc2626', marginTop: 2 }}>
                {calibrationOverview.EXPIRED ?? 0}
              </div>
            </div>

            <div style={{ background: 'var(--surface-secondary)', border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)' }}>NONE RECORDED</span>
              <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text)', marginTop: 2 }}>
                {calibrationOverview.NONE ?? 0}
              </div>
            </div>
          </div>

          {/* Preventive Maintenance Alert Strip */}
          <div style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
            <strong style={{ fontSize: '0.85rem', color: 'var(--text)' }}>
              🔔 Preventive Schedules Due (Next 7 Days)
            </strong>
            {preventiveScheduleAlerts.length === 0 ? (
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                All preventive maintenance schedules are up to date.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
                {preventiveScheduleAlerts.slice(0, 3).map((alert) => (
                  <div
                    key={alert.scheduleId}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      background: alert.overdue ? '#fee2e2' : 'var(--surface-secondary)',
                      padding: '6px 10px',
                      borderRadius: 6,
                      fontSize: '0.8rem'
                    }}
                  >
                    <div>
                      <strong style={{ color: alert.overdue ? '#991b1b' : 'var(--text)' }}>
                        {alert.equipmentName}
                      </strong>
                      <span style={{ color: 'var(--text-muted)', marginLeft: 6 }}>
                        ({alert.repeatPeriod || 'Periodic'})
                      </span>
                    </div>
                    <span style={{
                      fontWeight: 700,
                      color: alert.overdue ? '#dc2626' : '#d97706'
                    }}>
                      {alert.overdue ? 'OVERDUE' : `Due in ${alert.daysUntilDue}d`}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>

      {/* 5. Work Orders & Maintenance Status */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: 22
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <div>
            <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
              🛠️ Maintenance & Open Work Orders
            </h2>
            <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Severity breakdown and equipment currently offline for service
            </p>
          </div>
          <Link to="/maintenance" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
            Maintenance Module →
          </Link>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginBottom: 16 }}>
          {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((sev) => {
            const count = openWorkOrdersBySeverity[sev] ?? 0
            const sevColor = sev === 'CRITICAL' ? '#dc2626' : sev === 'HIGH' ? '#ea580c' : sev === 'MEDIUM' ? '#d97706' : '#64748b'
            return (
              <div key={sev} style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12, background: 'var(--surface-secondary)' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: sevColor }}>{sev} SEVERITY</span>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: sevColor, marginTop: 4 }}>
                  {count}
                </div>
              </div>
            )
          })}
        </div>

        {equipmentUnderMaintenance.length > 0 && (
          <div>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text)', marginBottom: 8 }}>
              Equipment Currently Under Maintenance:
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {equipmentUnderMaintenance.map((item) => (
                <span
                  key={item.equipmentId}
                  style={{
                    background: '#fff7ed',
                    border: '1px solid #ffedd5',
                    color: '#c2410c',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    padding: '4px 10px',
                    borderRadius: 6
                  }}
                >
                  ⚠️ {item.equipmentName} ({item.assetTag || 'Tag N/A'})
                </span>
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
