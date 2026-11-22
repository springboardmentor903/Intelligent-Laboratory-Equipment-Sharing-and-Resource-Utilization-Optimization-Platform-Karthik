import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { canManageSharing } from '../access/roleAccess.js'
import { formatAmount, formatSharedTime } from '../access/sharedFormatting.js'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/react/daygrid'
import timeGridPlugin from '@fullcalendar/react/timegrid'
import '@fullcalendar/react/skeleton.css'

function message(error) {
  return error?.response?.data?.message || 'Unable to complete this sharing action.'
}

function formatHours(minutes) {
  return `${(Number(minutes || 0) / 60).toFixed(2)} h`
}

function StatusBadge({ status, expired }) {
  const s = String(status || '').toUpperCase()
  let modifier = 'sharing-badge-pending'
  if (s === 'ACTIVE' || s === 'APPROVED') modifier = 'sharing-badge-active'
  else if (s === 'FORWARDED') modifier = 'sharing-badge-forwarded'
  else if (s === 'REJECTED' || s === 'REVOKED' || s === 'TERMINATED' || s === 'EXPIRED') {
    modifier = 'sharing-badge-rejected'
  }
  return (
    <span className={`sharing-badge ${modifier}`}>
      {s || 'UNKNOWN'}{expired ? ' (EXPIRED)' : ''}
    </span>
  )
}

function allowedWindowEvents(agreement, from, to) {
  if (!agreement) return []
  const startDate = agreement.startDate?.slice(0, 10) || from
  const endDate = agreement.endDate?.slice(0, 10) || to
  const first = from > startDate ? from : startDate
  const last = to < endDate ? to : endDate
  const events = []
  for (
    let day = new Date(`${first}T12:00:00`);
    day <= new Date(`${last}T12:00:00`);
    day.setDate(day.getDate() + 1)
  ) {
    const date = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
    events.push({
      title: 'Allowed booking window',
      start: `${date}T${agreement.allowedStartTime || '00:00:00'}`,
      end: `${date}T${agreement.allowedEndTime || '23:59:00'}`,
      display: 'background',
      backgroundColor: '#16a34a',
    })
  }
  return events
}

function Fees({ agreementId }) {
  const [params, setParams] = useSearchParams()
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const from = params.get('periodFrom') || ''
  const to = params.get('periodTo') || ''

  async function load() {
    setLoading(true)
    try {
      setError('')
      const response = await api.get(`/api/sharing/agreements/${agreementId}/fees`, {
        params: { from: from || undefined, to: to || undefined },
      })
      setResult(response.data)
    } catch (e) {
      setError(message(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [agreementId, from, to])

  function periodChange(key, value) {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set(key, value)
      else next.delete(key)
      return next
    })
  }

  return (
    <section className="sharing-fees-panel" style={{ marginTop: '16px', borderTop: '1px solid var(--border)', paddingTop: '16px' }}>
      <h3 style={{ fontSize: '1.05rem', fontWeight: 700, margin: '0 0 12px' }}>Usage Fees</h3>
      <div className="sharing-filter-panel" style={{ marginBottom: '12px' }}>
        <div className="sharing-filter-field">
          <label htmlFor={`fees-from-${agreementId}`}>From Date</label>
          <input
            id={`fees-from-${agreementId}`}
            type="date"
            value={from}
            onChange={(e) => periodChange('periodFrom', e.target.value)}
          />
        </div>
        <div className="sharing-filter-field">
          <label htmlFor={`fees-to-${agreementId}`}>To Date</label>
          <input
            id={`fees-to-${agreementId}`}
            type="date"
            value={to}
            onChange={(e) => periodChange('periodTo', e.target.value)}
          />
        </div>
      </div>

      {loading ? (
        <p role="status" className="muted-text">Loading usage fees…</p>
      ) : error ? (
        <div role="alert" className="module-alert" style={{ margin: '8px 0' }}>
          {error}{' '}
          <button type="button" className="button-secondary" onClick={load} style={{ marginLeft: '8px' }}>
            Retry
          </button>
        </div>
      ) : !result?.bookings?.length ? (
        <p className="muted-text">No usage fees for this period.</p>
      ) : (
        <>
          <div className="shared-agreement-banner" style={{ margin: '12px 0' }}>
            <p>
              <strong>Total Booked:</strong> {formatHours(result.bookedMinutes)} ·{' '}
              <strong>Used:</strong> {formatHours(result.usedMinutes)} ·{' '}
              <strong>Estimated Fee:</strong> {formatAmount(result.estimatedFee)} ·{' '}
              <strong>Actual Fee:</strong> {formatAmount(result.actualFee)}
            </p>
          </div>
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>Booking date</th>
                  <th>Booked hours</th>
                  <th>Used hours</th>
                  <th>Estimated fee</th>
                  <th>Actual fee</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                {result.bookings.map((item) => (
                  <tr key={item.bookingId}>
                    <td>{formatSharedTime(item.bookingDate)}</td>
                    <td>{formatHours(item.bookedMinutes)}</td>
                    <td>{formatHours(item.usedMinutes)}</td>
                    <td>{formatAmount(item.estimatedFee)}</td>
                    <td>{item.actualFee == null ? '—' : formatAmount(item.actualFee)}</td>
                    <td><strong>{formatAmount(item.totalFee)}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

export default function Sharing() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState(params.get('view') || 'discover')
  const [data, setData] = useState([])
  const [totalPages, setTotalPages] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [purpose, setPurpose] = useState('')
  const [dates, setDates] = useState({ startDate: '', endDate: '' })
  const [keyword, setKeyword] = useState(params.get('keyword') || '')
  const [category, setCategory] = useState(params.get('category') || '')
  const [debouncedKeyword, setDebouncedKeyword] = useState(keyword)
  const [managedEquipment, setManagedEquipment] = useState([])
  const [selectedEquipment, setSelectedEquipment] = useState('')
  const [listing, setListing] = useState({
    feePerHour: '',
    externalSharePercentage: 20,
    minimumNoticeHours: 24,
    maximumBookingHours: 4,
    terms: '',
    active: true,
  })
  const [shareable, setShareable] = useState(false)
  const [institutions, setInstitutions] = useState([])
  const [schedule, setSchedule] = useState(null)
  const [scheduleAccess, setScheduleAccess] = useState(null)
  const [scheduleRange, setScheduleRange] = useState({
    from: new Date().toISOString().slice(0, 10),
    to: new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10),
  })

  const canAdmin = canManageSharing(user?.roles)
  const canRequest =
    user?.institutionId &&
    user?.roles?.some((role) => ['RESEARCHER_STUDENT', 'DEPARTMENT_HEAD'].includes(role))

  useEffect(() => {
    setKeyword(params.get('keyword') || '')
    setCategory(params.get('category') || '')
    setTab(params.get('view') || 'discover')
  }, [params])

  async function load() {
    setBusy(true)
    setError('')
    try {
      const requestList = ['mine', 'outgoing', 'incoming', 'department'].includes(tab)
      const endpoint =
        tab === 'discover'
          ? '/api/sharing/listings'
          : requestList
          ? `/api/sharing/requests?view=${tab}`
          : tab === 'access'
          ? '/api/sharing/my-access'
          : '/api/sharing/agreements'
      const result = await api.get(endpoint, {
        params:
          tab === 'discover'
            ? {
                keyword: keyword || undefined,
                category: category || undefined,
                institutionId: params.get('institutionId') || undefined,
                page: Number(params.get('page') || 0),
              }
            : requestList
            ? { page: Number(params.get('page') || 0), size: 12 }
            : undefined,
      })
      setData(result.data.content || result.data)
      setTotalPages(result.data.totalPages || 0)
    } catch (e) {
      setError(message(e))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword)
      setParams((p) => {
        const next = new URLSearchParams(p)
        if (keyword) next.set('keyword', keyword)
        else next.delete('keyword')
        next.delete('page')
        return next
      })
    }, 500)
    return () => clearTimeout(timer)
  }, [keyword])

  useEffect(() => {
    load()
  }, [tab, params, category, debouncedKeyword])

  useEffect(() => {
    if (canAdmin) {
      api
        .get('/api/sharing/manageable-equipment')
        .then((r) => setManagedEquipment(r.data || []))
        .catch((e) => setError(message(e)))
    }
  }, [canAdmin])

  useEffect(() => {
    api
      .get('/api/institutions')
      .then((r) => setInstitutions(r.data || []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!selectedEquipment) return
    Promise.all([
      api.get(`/api/sharing/equipment/${selectedEquipment}/listing`).catch(() => ({ data: null })),
      api.get('/api/equipment'),
    ])
      .then(([l, e]) => {
        const value = l.data
        setListing(
          value?.id
            ? {
                feePerHour: value.feePerHour,
                externalSharePercentage: value.externalSharePercentage,
                minimumNoticeHours: value.minimumNoticeHours,
                maximumBookingHours: value.maximumBookingHours,
                terms: value.terms,
                active: value.active,
              }
            : {
                feePerHour: '',
                externalSharePercentage: 20,
                minimumNoticeHours: 24,
                maximumBookingHours: 4,
                terms: '',
                active: true,
              },
        )
        const item = (e.data || []).find((x) => String(x.id) === selectedEquipment)
        setShareable(Boolean(item?.shareable))
      })
      .catch(() => {})
  }, [selectedEquipment])

  function selectTab(value) {
    setTab(value)
    setFeedback('')
    setError('')
    setParams(value === 'discover' ? {} : { view: value })
  }

  async function requestAccess(equipmentId) {
    if (!purpose.trim() || !dates.startDate || !dates.endDate) {
      setError('Enter a purpose and access dates first.')
      return
    }
    try {
      await api.post('/api/sharing/requests', { equipmentId, purpose: purpose.trim(), ...dates })
      setPurpose('')
      setError('')
      setFeedback('Access request submitted successfully.')
      selectTab('mine')
    } catch (e) {
      setError(message(e))
    }
  }

  async function action(id, actionName) {
    let remarks = ''
    let allowedStartTime
    let allowedEndTime
    if (actionName === 'REJECT') {
      remarks = window.prompt('Rejection remarks (required)') || ''
      if (!remarks.trim()) return
    }
    if (actionName === 'APPROVE') {
      if (!window.confirm('Approve this request and create an active agreement?')) return
      allowedStartTime = window.prompt('Optional allowed start time (HH:mm). Leave blank for all hours.') || ''
      if (allowedStartTime) allowedEndTime = window.prompt('Allowed end time (HH:mm)') || ''
    }
    try {
      await api.post(`/api/sharing/requests/${id}/decision`, {
        action: actionName,
        remarks,
        allowedStartTime,
        allowedEndTime,
      })
      setFeedback(`Request ${actionName.toLowerCase()}ed successfully.`)
      load()
    } catch (e) {
      setError(message(e))
      load()
    }
  }

  async function withdraw(id) {
    try {
      await api.post(`/api/sharing/requests/${id}/withdraw`)
      setFeedback('Request withdrawn.')
      load()
    } catch (e) {
      setError(message(e))
      load()
    }
  }

  async function saveListing(e) {
    e.preventDefault()
    try {
      await api.patch(`/api/sharing/equipment/${selectedEquipment}/shareable`, { shareable })
      await api.put(`/api/sharing/equipment/${selectedEquipment}/listing`, {
        ...listing,
        feePerHour: Number(listing.feePerHour),
      })
      setError('')
      setFeedback('Equipment sharing listing saved successfully.')
    } catch (e) {
      setError(message(e))
    }
  }

  async function loadSchedule(range = scheduleRange) {
    if (!selectedEquipment) return
    try {
      const [r, access] = await Promise.all([
        api.get(`/api/sharing/equipment/${selectedEquipment}/schedule`, { params: range }),
        api.get('/api/sharing/my-access'),
      ])
      setSchedule(r.data)
      setScheduleAccess(
        (access.data || []).find((x) => String(x.equipment?.id) === String(selectedEquipment)) || null,
      )
    } catch (e) {
      setError(message(e))
    }
  }

  function visibleDates(info) {
    const from = info.startStr.slice(0, 10)
    const to = new Date(new Date(info.endStr).getTime() - 86400000).toISOString().slice(0, 10)
    if (from !== scheduleRange.from || to !== scheduleRange.to) {
      const next = { from, to }
      setScheduleRange(next)
      if (selectedEquipment) loadSchedule(next)
    }
  }

  async function revoke(path) {
    if (!window.confirm('Confirm this revocation?')) return
    const reason = window.prompt('Revocation reason (required)') || ''
    if (!reason.trim()) {
      setError('A revocation reason is required.')
      return
    }
    try {
      await api.post(path, { reason })
      setFeedback('Revocation processed successfully.')
      await load()
    } catch (e) {
      setError(message(e))
    }
  }

  const rows = Array.isArray(data) ? data : []

  const navTabs = [
    ['discover', 'Discover Equipment'],
    ['mine', 'My Requests'],
    ['schedule', 'Shared Schedule'],
    ...(user?.roles?.includes('DEPARTMENT_HEAD') ? [['department', 'Department Requests']] : []),
    ...(canAdmin
      ? [
          ['outgoing', 'Outgoing Proposals'],
          ['incoming', 'Incoming Requests'],
          ['agreements', 'Bilateral Agreements'],
        ]
      : []),
    ['access', 'My Shared Access'],
  ]

  return (
    <main className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">INTER-INSTITUTION PLATFORM</span>
          <h1>Equipment Sharing</h1>
          <p>Discover shareable equipment across partner institutions, submit access proposals, and manage bilateral agreements.</p>
        </div>
      </section>

      <nav className="sharing-tabs-nav" aria-label="Sharing sections">
        {navTabs.map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={`sharing-tab-btn ${tab === id ? 'button-primary' : 'button-secondary'}`}
            onClick={() => selectTab(id)}
          >
            {label}
          </button>
        ))}
      </nav>

      {error && <div className="module-alert" role="alert">{error}</div>}
      {feedback && <div className="auth-success" role="status">{feedback}</div>}

      {/* DISCOVER TAB */}
      {tab === 'discover' && (
        <>
          <section className="booking-form-card">
            <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 16px' }}>Filter & Request Access</h2>
            <div className="sharing-filter-panel">
              <div className="sharing-filter-field">
                <label htmlFor="search-keyword">Keyword Search</label>
                <input
                  id="search-keyword"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="Equipment name, tag, or terms…"
                />
              </div>

              <div className="sharing-filter-field">
                <label htmlFor="search-category">Category</label>
                <input
                  id="search-category"
                  value={category}
                  placeholder="e.g. Spectroscopy, Microscopy"
                  onChange={(e) => {
                    const val = e.target.value
                    setCategory(val)
                    setParams((p) => {
                      const next = new URLSearchParams(p)
                      if (val) next.set('category', val)
                      else next.delete('category')
                      return next
                    })
                  }}
                />
              </div>

              <div className="sharing-filter-field">
                <label htmlFor="filter-institution">Lending Institution</label>
                <select
                  id="filter-institution"
                  value={params.get('institutionId') || ''}
                  onChange={(e) => {
                    const val = e.target.value
                    setParams((p) => {
                      const next = new URLSearchParams(p)
                      if (val) next.set('institutionId', val)
                      else next.delete('institutionId')
                      next.delete('page')
                      return next
                    })
                  }}
                >
                  <option value="">All other institutions</option>
                  {institutions
                    .filter((i) => String(i.id) !== String(user?.institutionId))
                    .map((i) => (
                      <option key={i.id} value={i.id}>
                        {i.name}
                      </option>
                    ))}
                </select>
              </div>
            </div>

            {canRequest && (
              <div style={{ marginTop: '16px', borderTop: '1px solid var(--border)', paddingTop: '16px' }}>
                <h3 style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-muted)', margin: '0 0 12px' }}>
                  PROPOSAL DETAILS (USED WHEN REQUESTING ACCESS)
                </h3>
                <div className="sharing-filter-panel">
                  <div className="sharing-filter-field" style={{ gridColumn: 'span 2' }}>
                    <label htmlFor="req-purpose">Research Purpose & Scope</label>
                    <textarea
                      id="req-purpose"
                      maxLength={500}
                      rows={2}
                      value={purpose}
                      onChange={(e) => setPurpose(e.target.value)}
                      placeholder="Describe the research project or analysis requirements…"
                    />
                  </div>
                  <div className="sharing-filter-field">
                    <label htmlFor="req-start-date">Required Start Date</label>
                    <input
                      id="req-start-date"
                      type="datetime-local"
                      value={dates.startDate}
                      onChange={(e) => setDates({ ...dates, startDate: e.target.value })}
                    />
                  </div>
                  <div className="sharing-filter-field">
                    <label htmlFor="req-end-date">Required End Date</label>
                    <input
                      id="req-end-date"
                      type="datetime-local"
                      value={dates.endDate}
                      onChange={(e) => setDates({ ...dates, endDate: e.target.value })}
                    />
                  </div>
                </div>
              </div>
            )}
          </section>

          {busy ? (
            <div className="module-state" role="status">
              <span className="loading-spinner" />
              <p>Discovering shared equipment listings…</p>
            </div>
          ) : rows.length === 0 ? (
            <div className="sharing-empty-state">
              <h3>No shared equipment listings available</h3>
              <p>Check back later or adjust your keyword and category filters.</p>
            </div>
          ) : (
            <div className="sharing-card-grid">
              {rows.map((item) => (
                <article className="sharing-card" key={item.listingId}>
                  <div className="sharing-card-header">
                    <div>
                      <h2>{item.equipmentName}</h2>
                      <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--text-muted)' }}>
                        {item.assetTag} · <strong>{item.category}</strong>
                      </p>
                    </div>
                    {item.requestStatus ? (
                      <StatusBadge status={item.requestStatus} />
                    ) : (
                      <span className="sharing-badge sharing-badge-active">Available to Share</span>
                    )}
                  </div>

                  <div className="sharing-meta-list">
                    <div className="sharing-meta-item">
                      <span>Owner Institution:</span>
                      <strong>{item.ownerInstitution || 'External Institution'}</strong>
                    </div>
                    <div className="sharing-meta-item">
                      <span>Rate & Terms:</span>
                      <strong>₹{item.feePerHour}/hour</strong>
                    </div>
                    <div className="sharing-meta-item">
                      <span>Constraints:</span>
                      <span>Min notice: {item.minimumNoticeHours}h · Max duration: {item.maximumBookingHours}h</span>
                    </div>
                  </div>

                  {item.description && (
                    <p style={{ fontSize: '13.5px', color: 'var(--text-muted)', margin: '4px 0' }}>
                      {item.description}
                    </p>
                  )}

                  {item.terms && (
                    <div className="sharing-terms-box">
                      <strong>Sharing Terms:</strong> {item.terms}
                    </div>
                  )}

                  <div className="sharing-card-actions">
                    {item.requestStatus === 'ACTIVE' ? (
                      <>
                        <span style={{ fontSize: '12.5px', color: 'var(--success)' }}>
                          Agreement active until {new Date(item.agreementEndDate).toLocaleDateString()}
                        </span>
                        <Link
                          className="button-primary"
                          to={`/bookings?equipmentId=${item.equipmentId}&agreementId=${item.agreementId}`}
                        >
                          Book via Agreement
                        </Link>
                      </>
                    ) : item.requestStatus ? (
                      <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                        Proposal Status: <strong>{item.requestStatus}</strong>
                      </span>
                    ) : canRequest ? (
                      <button
                        type="button"
                        className="button-primary"
                        onClick={() => requestAccess(item.equipmentId)}
                      >
                        Request Access
                      </button>
                    ) : (
                      <span style={{ fontSize: '12.5px', color: 'var(--text-muted)' }}>
                        Institutional membership required to request
                      </span>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <nav className="pagination-bar" aria-label="Sharing pages">
              <button
                type="button"
                disabled={Number(params.get('page') || 0) <= 0}
                onClick={() =>
                  setParams((p) => {
                    const next = new URLSearchParams(p)
                    next.set('page', String(Math.max(0, Number(p.get('page') || 0) - 1)))
                    return next
                  })
                }
              >
                Previous
              </button>
              <span>
                Page {Number(params.get('page') || 0) + 1} of {Math.max(1, totalPages)}
              </span>
              <button
                type="button"
                disabled={Number(params.get('page') || 0) + 1 >= totalPages}
                onClick={() =>
                  setParams((p) => {
                    const next = new URLSearchParams(p)
                    next.set('page', String(Number(p.get('page') || 0) + 1))
                    return next
                  })
                }
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}

      {/* SCHEDULE TAB */}
      {tab === 'schedule' && (
        <section className="booking-form-card">
          <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 16px' }}>Shared Equipment Schedule</h2>
          <div className="sharing-filter-panel" style={{ alignItems: 'flex-end', marginBottom: '16px' }}>
            <div className="sharing-filter-field">
              <label htmlFor="sched-equipment-id">Equipment ID</label>
              <input
                id="sched-equipment-id"
                type="number"
                min="1"
                placeholder="Enter equipment ID"
                value={selectedEquipment}
                onChange={(e) => setSelectedEquipment(e.target.value)}
              />
            </div>
            <button
              type="button"
              className="button-primary"
              style={{ minHeight: '42px', padding: '0 20px' }}
              onClick={() => loadSchedule()}
            >
              Load Schedule
            </button>
          </div>

          {scheduleAccess && (
            <div className="shared-agreement-banner">
              <h4>Active Agreement Window</h4>
              <p>
                Allowed Booking Hours:{' '}
                <strong>
                  {scheduleAccess.agreement.allowedStartTime || 'All Day'} –{' '}
                  {scheduleAccess.agreement.allowedEndTime || 'All Day'}
                </strong>
              </p>
              <p>
                Agreement Termination Date:{' '}
                <strong>{new Date(scheduleAccess.agreement.endDate).toLocaleDateString()}</strong>
              </p>
            </div>
          )}

          {schedule && (
            <div style={{ marginTop: '16px' }}>
              <div style={{ marginBottom: '12px' }}>
                <p style={{ margin: '0 0 6px', fontSize: '13.5px', color: 'var(--text-muted)' }}>
                  External Allocation: <strong>{schedule.externalMinutesUsed} min</strong> used of{' '}
                  <strong>{schedule.weeklyExternalShareLimit} min</strong> quota (
                  {schedule.externalSharePercentage}% target limit)
                </p>
                <progress
                  style={{ width: '100%', height: '8px', borderRadius: '4px' }}
                  max={Math.max(1, schedule.weeklyExternalShareLimit)}
                  value={schedule.externalMinutesUsed}
                />
              </div>

              <div className="analytics-chart-legend" style={{ margin: '14px 0' }}>
                <span className="legend-chip">
                  <span className="legend-color-dot" style={{ background: '#2563eb' }} />
                  Internal Reservations
                </span>
                <span className="legend-chip">
                  <span className="legend-color-dot" style={{ background: '#b45309' }} />
                  External Shared Bookings
                </span>
                <span className="legend-chip">
                  <span className="legend-color-dot" style={{ background: '#16a34a' }} />
                  Allowed Window
                </span>
              </div>

              <FullCalendar
                plugins={[dayGridPlugin, timeGridPlugin]}
                initialView="timeGridWeek"
                headerToolbar={{
                  left: 'prev,next today',
                  center: 'title',
                  right: 'timeGridDay,timeGridWeek',
                }}
                datesSet={visibleDates}
                events={[
                  ...schedule.slots.map((slot) => ({
                    title: slot.type,
                    start: slot.start,
                    end: slot.end,
                    backgroundColor: slot.type === 'EXTERNAL' ? '#b45309' : '#2563eb',
                    extendedProps: { partnerInstitution: slot.partnerInstitution },
                  })),
                  ...allowedWindowEvents(
                    scheduleAccess?.agreement,
                    scheduleRange.from,
                    scheduleRange.to,
                  ),
                ]}
                eventDidMount={({ event, el }) => {
                  if (event.extendedProps.partnerInstitution) {
                    el.title = event.extendedProps.partnerInstitution
                  }
                }}
                height="auto"
              />
            </div>
          )}
        </section>
      )}

      {/* REQUESTS & AGREEMENTS TABS */}
      {['mine', 'outgoing', 'incoming', 'department', 'agreements', 'access'].includes(tab) && (
        <>
          {busy ? (
            <div className="module-state" role="status">
              <span className="loading-spinner" />
              <p>Loading sharing records…</p>
            </div>
          ) : rows.length === 0 ? (
            <div className="sharing-empty-state">
              <h3>No records found</h3>
              <p>There are no active {tab.replace('-', ' ')} records in this view.</p>
            </div>
          ) : (
            <div className="sharing-card-grid">
              {rows.map((value, index) => {
                const row = value.agreement ? value.agreement : value
                const eq = value.equipment
                const ownerManager =
                  canAdmin &&
                  (user?.roles?.includes('SYSTEM_ADMIN') ||
                    String(row.ownerInstitutionId) === String(user?.institutionId))

                return (
                  <article className="sharing-card" key={row.id || index}>
                    <div className="sharing-card-header">
                      <div>
                        <h2>{eq?.name || `Access Request #${row.id}`}</h2>
                        <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--text-muted)' }}>
                          ID: #{row.id} · {eq?.assetTag ? `Tag: ${eq.assetTag}` : 'Inter-Institution'}
                        </p>
                      </div>
                      <StatusBadge status={row.status} expired={value.expired} />
                    </div>

                    <div className="sharing-meta-list">
                      {row.purpose && (
                        <div className="sharing-meta-item">
                          <span>Purpose:</span>
                          <strong>{row.purpose}</strong>
                        </div>
                      )}
                      <div className="sharing-meta-item">
                        <span>Period:</span>
                        <span>{formatSharedTime(row.startDate || row.startTime)} – {formatSharedTime(row.endDate || row.endTime)}</span>
                      </div>
                      {row.feePerHour != null && (
                        <div className="sharing-meta-item">
                          <span>Fee:</span>
                          <strong>{formatAmount(row.feePerHour)}/hr</strong>
                        </div>
                      )}
                    </div>

                    {tab === 'mine' && (
                      <div className="sharing-terms-box">
                        <p style={{ margin: '0 0 4px' }}>
                          <strong>Home Review:</strong> {row.requesterReviewedBy ? `Reviewer #${row.requesterReviewedBy} (${row.requesterReviewRemarks || 'Approved'})` : 'Pending'}
                        </p>
                        <p style={{ margin: 0 }}>
                          <strong>Partner Decision:</strong> {row.reviewedBy ? `Reviewer #${row.reviewedBy} (${row.reviewRemarks || 'Approved'})` : 'Awaiting Decision'}
                        </p>
                      </div>
                    )}

                    <div className="sharing-card-actions">
                      {tab === 'mine' && ['PENDING', 'FORWARDED'].includes(row.status) && (
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => withdraw(row.id)}
                        >
                          Withdraw Request
                        </button>
                      )}

                      {tab === 'outgoing' && (
                        <>
                          <button
                            type="button"
                            className="button-primary"
                            onClick={() => action(row.id, 'FORWARD')}
                          >
                            Forward to Partner
                          </button>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => action(row.id, 'REJECT')}
                          >
                            Reject
                          </button>
                        </>
                      )}

                      {tab === 'incoming' && (
                        <>
                          <button
                            type="button"
                            className="button-primary"
                            onClick={() => action(row.id, 'APPROVE')}
                          >
                            Approve & Create Agreement
                          </button>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => action(row.id, 'REJECT')}
                          >
                            Reject
                          </button>
                        </>
                      )}

                      {tab === 'access' && (
                        <Link
                          className="button-primary"
                          to={`/bookings?equipmentId=${eq?.id}&agreementId=${row.id}`}
                        >
                          Create Booking
                        </Link>
                      )}

                      {tab === 'agreements' && ownerManager && (
                        <>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => revoke(`/api/sharing/agreements/${row.id}/revoke`)}
                          >
                            Revoke Agreement
                          </button>
                          {(value.grants || []).map((grant) => (
                            <div key={grant.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%' }}>
                              <span style={{ fontSize: '12.5px', color: 'var(--text-muted)' }}>
                                Grant #{grant.id}: {grant.status}
                              </span>
                              {grant.status === 'ACTIVE' && (
                                <button
                                  type="button"
                                  className="button-secondary"
                                  style={{ padding: '3px 8px', fontSize: '11.5px' }}
                                  onClick={() => revoke(`/api/sharing/grants/${grant.id}/revoke`)}
                                >
                                  Revoke Grant
                                </button>
                              )}
                            </div>
                          ))}
                        </>
                      )}
                    </div>

                    {(tab === 'agreements' || tab === 'access') && <Fees agreementId={row.id} />}
                  </article>
                )
              })}
            </div>
          )}

          {['mine', 'outgoing', 'incoming', 'department'].includes(tab) && totalPages > 1 && (
            <nav className="pagination-bar" aria-label="Sharing pages">
              <button
                type="button"
                disabled={Number(params.get('page') || 0) <= 0}
                onClick={() =>
                  setParams((p) => {
                    const next = new URLSearchParams(p)
                    next.set('page', String(Math.max(0, Number(p.get('page') || 0) - 1)))
                    return next
                  })
                }
              >
                Previous
              </button>
              <span>
                Page {Number(params.get('page') || 0) + 1} of {Math.max(1, totalPages)}
              </span>
              <button
                type="button"
                disabled={Number(params.get('page') || 0) + 1 >= totalPages}
                onClick={() =>
                  setParams((p) => {
                    const next = new URLSearchParams(p)
                    next.set('page', String(Number(p.get('page') || 0) + 1))
                    return next
                  })
                }
              >
                Next
              </button>
            </nav>
          )}

          {tab === 'mine' && rows.length > 0 && (
            <section className="sharing-timeline-card">
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 8px' }}>
                Two-Stage Approval Progression
              </h2>
              <p style={{ margin: '0 0 16px', fontSize: '13.5px', color: 'var(--text-muted)' }}>
                Track the institutional review and equipment owner decision timeline for submitted proposals.
              </p>
              <div className="sharing-timeline-steps">
                {rows.map((request) => (
                  <article className="sharing-timeline-step" key={request.id}>
                    <div>
                      <strong>Request #{request.id}</strong> — <StatusBadge status={request.status} />
                      <p>Submitted: {formatSharedTime(request.createdAt)}</p>
                      <p>
                        Stage 1 (Home Institution Review):{' '}
                        {request.requesterReviewedBy
                          ? `Reviewer #${request.requesterReviewedBy} · ${formatSharedTime(request.requesterReviewedAt)} · Remarks: ${request.requesterReviewRemarks || 'Approved'}`
                          : 'Waiting for institution review'}
                      </p>
                      <p>
                        Stage 2 (Lending Partner Decision):{' '}
                        {request.reviewedBy
                          ? `Reviewer #${request.reviewedBy} · ${formatSharedTime(request.reviewedAt)} · Remarks: ${request.reviewRemarks || 'Approved'}`
                          : request.status === 'FORWARDED'
                          ? 'Awaiting owner institution decision'
                          : 'Not reached'}
                      </p>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}
        </>
      )}

      {/* ADMIN EQUIPMENT SHARING MANAGEMENT */}
      {canAdmin && (
        <section className="booking-form-card" style={{ marginTop: '28px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
            <div>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 4px' }}>
                Share This Equipment
              </h2>
              <p style={{ margin: 0, fontSize: '13.5px', color: 'var(--text-muted)' }}>
                Publish equipment to the inter-institution sharing catalog and configure external parameters.
              </p>
            </div>
            <Link className="button-secondary" to="/equipment">
              View Catalog
            </Link>
          </div>

          <form className="booking-form" onSubmit={saveListing}>
            <div className="sharing-filter-panel">
              <div className="sharing-filter-field" style={{ gridColumn: 'span 2' }}>
                <label htmlFor="select-owner-eq">Select Laboratory Equipment</label>
                <select
                  id="select-owner-eq"
                  required
                  value={selectedEquipment}
                  onChange={(e) => setSelectedEquipment(e.target.value)}
                >
                  <option value="">Select owner equipment to configure sharing</option>
                  {managedEquipment.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} · {item.assetTag}
                    </option>
                  ))}
                </select>
              </div>

              {selectedEquipment && (
                <>
                  <div className="sharing-filter-field" style={{ justifyContent: 'center' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={shareable}
                        onChange={(e) => setShareable(e.target.checked)}
                      />
                      <span>Mark equipment shareable</span>
                    </label>
                  </div>

                  <div className="sharing-filter-field">
                    <label htmlFor="eq-fee">Fee per Hour (₹)</label>
                    <input
                      id="eq-fee"
                      type="number"
                      min="0"
                      step="0.01"
                      required
                      placeholder="e.g. 1500"
                      value={listing.feePerHour}
                      onChange={(e) => setListing({ ...listing, feePerHour: e.target.value })}
                    />
                  </div>

                  <div className="sharing-filter-field">
                    <label htmlFor="eq-share-pct">External Share Target (%)</label>
                    <input
                      id="eq-share-pct"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      value={listing.externalSharePercentage}
                      onChange={(e) =>
                        setListing({ ...listing, externalSharePercentage: Number(e.target.value) })
                      }
                    />
                  </div>

                  <div className="sharing-filter-field">
                    <label htmlFor="eq-notice">Minimum Notice (Hours)</label>
                    <input
                      id="eq-notice"
                      type="number"
                      min="0"
                      value={listing.minimumNoticeHours}
                      onChange={(e) =>
                        setListing({ ...listing, minimumNoticeHours: Number(e.target.value) })
                      }
                    />
                  </div>

                  <div className="sharing-filter-field">
                    <label htmlFor="eq-duration">Maximum Duration (Hours)</label>
                    <input
                      id="eq-duration"
                      type="number"
                      min="1"
                      value={listing.maximumBookingHours}
                      onChange={(e) =>
                        setListing({ ...listing, maximumBookingHours: Number(e.target.value) })
                      }
                    />
                  </div>

                  <div className="sharing-filter-field" style={{ gridColumn: 'span 2' }}>
                    <label htmlFor="eq-terms">Sharing Terms & Usage Conditions</label>
                    <textarea
                      id="eq-terms"
                      maxLength={2000}
                      rows={3}
                      placeholder="Specify safety certifications, lab policies, operator assistance requirements…"
                      value={listing.terms}
                      onChange={(e) => setListing({ ...listing, terms: e.target.value })}
                    />
                  </div>

                  <div className="sharing-filter-field" style={{ justifyContent: 'center' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={listing.active}
                        onChange={(e) => setListing({ ...listing, active: e.target.checked })}
                      />
                      <span>Listing active and discoverable</span>
                    </label>
                  </div>

                  <div style={{ gridColumn: 'span 2', marginTop: '10px' }}>
                    <button type="submit" className="button-primary" style={{ minHeight: '42px', padding: '0 24px' }}>
                      Save Sharing Listing
                    </button>
                  </div>
                </>
              )}
            </div>
          </form>
        </section>
      )}
    </main>
  )
}
