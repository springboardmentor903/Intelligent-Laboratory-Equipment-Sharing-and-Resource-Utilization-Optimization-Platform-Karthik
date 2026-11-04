import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  canRequestBooking,
  canApproveBookings,
  getBookingRecordsUrl,
} from '../access/roleAccess.js'
import {
  BOOKING_REFRESH_EVENT,
  getBookingActionError,
  getBookingActions,
  getCheckInAvailability,
  performBookingAction,
  formatBookingStatus,
  getAvailabilityRequest,
} from '../access/bookingWorkflow.js'
import { buildBookingPayload } from '../access/bookingDateTime.js'
import SchedulingRecommendations from '../components/booking/SchedulingRecommendations.jsx'
import PendingApprovalsQueue from '../components/booking/PendingApprovalsQueue.jsx'
import WaitlistPanel from '../components/booking/WaitlistPanel.jsx'

const initialForm = {
  equipmentId: '',
  startTime: '',
  endTime: '',
  purpose: '',
}

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)

  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString()
}

function getStatusClass(status) {
  return `booking-status booking-status-${String(
    status || 'unknown',
  ).toLowerCase()}`
}

function getErrorMessage(error, fallback) {
  if (error?.response?.status === 403) {
    return 'Access denied. Your role or account scope cannot view these booking records.'
  }
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    fallback
  )
}

export default function Bookings() {
  const { user } = useAuth()

  const [bookings, setBookings] = useState([])
  const [equipment, setEquipment] = useState([])
  const [form, setForm] = useState(initialForm)

  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [actionInProgress, setActionInProgress] = useState(null)
  const [actionError, setActionError] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [selectedAuditBookingId, setSelectedAuditBookingId] = useState(null)
  const [auditLogs, setAuditLogs] = useState([])
  const [auditLoading, setAuditLoading] = useState(false)
  const [auditError, setAuditError] = useState('')
  const actionLock = useRef(false)

  const [activeTab, setActiveTab] = useState('bookings')
  const [pendingCount, setPendingCount] = useState(0)
  const [waitlistCount, setWaitlistCount] = useState(0)
  const [recurringBooking, setRecurringBooking] = useState(false)
  const [recurringFrequency, setRecurringFrequency] = useState('WEEKLY')
  const [recurringCount, setRecurringCount] = useState(4)
  const [recurringPreview, setRecurringPreview] = useState(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [recommendations, setRecommendations] = useState(null)
  const [recommendationsLoading, setRecommendationsLoading] = useState(false)

  const loadCounts = useCallback(async () => {
    if (canApproveBookings(user?.roles)) {
      try {
        const pendingResp = await api.get('/api/bookings/pending')
        setPendingCount(Array.isArray(pendingResp.data) ? pendingResp.data.length : 0)
      } catch {}
    }
    try {
      const waitlistResp = await api.get('/api/waitlist/my')
      setWaitlistCount(Array.isArray(waitlistResp.data) ? waitlistResp.data.length : 0)
    } catch {}
  }, [user?.roles])

  const handleSelectRecommendedSlot = (startIso, endIso) => {
    const formatToLocalInput = (iso) => {
      if (!iso) return ''
      const d = new Date(iso)
      if (Number.isNaN(d.getTime())) return String(iso).slice(0, 16)
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    }
    setForm((prev) => ({
      ...prev,
      startTime: formatToLocalInput(startIso),
      endTime: formatToLocalInput(endIso),
    }))
    setRecommendations(null)
    setError('')
    setSuccess('Applied recommended time slot! Click submit to confirm.')
  }

  const handleSelectRecommendedEquipment = (equipmentId) => {
    setForm((prev) => ({
      ...prev,
      equipmentId: String(equipmentId),
    }))
    setRecommendations(null)
    setError('')
    setSuccess('Switched to recommended equipment!')
  }

  const handleJoinWaitlistFromRecommendation = async () => {
    try {
      setSubmitting(true)
      const bookingPayload = buildBookingPayload(form)
      await api.post('/api/waitlist', {
        equipmentId: Number(form.equipmentId),
        requestedStartTime: bookingPayload.startTime,
        requestedEndTime: bookingPayload.endTime,
        notes: form.purpose || 'Joined via scheduling recommendation',
      })
      setSuccess('Successfully added to waitlist! Check the My Waitlist tab.')
      setRecommendations(null)
      setActiveTab('waitlist')
      loadCounts()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to join waitlist.')
    } finally {
      setSubmitting(false)
    }
  }

  const handlePreviewRecurring = async () => {
    if (!form.equipmentId || !form.startTime || !form.endTime) {
      setError('Please select equipment, start time, and end time to preview occurrences.')
      return
    }
    setPreviewLoading(true)
    setError('')
    try {
      const bookingPayload = buildBookingPayload(form)
      const resp = await api.post('/api/bookings/recurring/preview', {
        equipmentId: Number(form.equipmentId),
        startTime: bookingPayload.startTime,
        endTime: bookingPayload.endTime,
        purpose: form.purpose?.trim() || undefined,
        recurrenceRule: recurringFrequency,
        recurrenceCount: Number(recurringCount),
      })
      setRecurringPreview(resp.data)
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to preview recurring occurrences.')
    } finally {
      setPreviewLoading(false)
    }
  }

  async function handleCancelSeries(booking) {
    if (!window.confirm(`Are you sure you want to cancel the entire recurring series for booking #${booking.id}?`)) {
      return
    }
    setActionInProgress(`${booking.id}:cancel-series`)
    setActionError('')
    try {
      const res = await api.post(`/api/bookings/recurring/${booking.id}/cancel-series`, {
        reason: 'User cancelled recurring series',
      })
      setSuccess(res.data?.message || 'Recurring series cancelled.')
      await loadData()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to cancel recurring series.')
    } finally {
      setActionInProgress(null)
    }
  }

  async function viewAudit(bookingId) {
    setSelectedAuditBookingId(bookingId)
    setAuditLoading(true)
    setAuditError('')
    try {
      const response = await api.get(`/api/bookings/${bookingId}/audit`)
      setAuditLogs(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      setAuditError(err.response?.data?.message || 'Failed to load audit history.')
    } finally {
      setAuditLoading(false)
    }
  }

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const bookingsUrl = getBookingRecordsUrl(user?.roles, user?.id)
      const results = await Promise.allSettled([
        bookingsUrl
          ? api.get(bookingsUrl)
          : Promise.reject(new Error('Booking history is unavailable for this account.')),
        api.get('/api/equipment'),
      ])

      const [bookingResult, equipmentResult] = results
      const errors = []

      if (bookingResult.status === 'fulfilled') {
        setBookings(
          Array.isArray(bookingResult.value.data)
            ? bookingResult.value.data
            : [],
        )
      } else {
        if (bookingResult.reason?.response?.status === 403) {
          setBookings([])
        }
        errors.push(
          `Bookings: ${getErrorMessage(
            bookingResult.reason,
            'Unable to load booking records.',
          )}`,
        )
      }

      if (equipmentResult.status === 'fulfilled') {
        setEquipment(
          Array.isArray(equipmentResult.value.data)
            ? equipmentResult.value.data
            : [],
        )
      } else {
        errors.push(
          `Equipment: ${getErrorMessage(
            equipmentResult.reason,
            'Unable to load equipment.',
          )}`,
        )
      }

      if (errors.length > 0) {
        setError(errors.join(' | '))
      }
      void loadCounts()
    } catch (err) {
      setError(
        getErrorMessage(err, 'Unable to load booking information.'),
      )
    } finally {
      setLoading(false)
    }
  }, [user, loadCounts])

  useEffect(() => {
    // Start the asynchronous fetch after React has committed this render.
    // The callback itself owns the loading/error state transitions.
    void Promise.resolve().then(loadData)
  }, [loadData])

  function handleChange(event) {
    const { name, value } = event.target

    setForm((current) => ({
      ...current,
      [name]: value,
    }))

    setError('')
    setSuccess('')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSuccess('')

    if (!user?.id) {
      setError('Your user ID is unavailable. Please sign in again.')
    }

    if (
      !form.equipmentId ||
      !form.startTime ||
      !form.endTime ||
      !form.purpose.trim()
    ) {
      setError('Complete all booking fields before submitting.')
      return
    }

    const selectedEquipment = equipment.find(
      (item) => String(item.id) === form.equipmentId,
    )

    if (!selectedEquipment) {
      setError('Select a valid equipment item.')
      return
    }

    let bookingPayload
    try {
      bookingPayload = buildBookingPayload(form)
    } catch {
      setError('Enter valid booking start and end times.')
      return
    }

    if (bookingPayload.endTime <= bookingPayload.startTime) {
      setError('The end time must be after the start time.')
      return
    }

    if (
      selectedEquipment.status &&
      !['AVAILABLE', 'BOOKED'].includes(
        String(selectedEquipment.status).toUpperCase(),
      )
    ) {
      setError(
        'The selected equipment is not currently marked as available.',
      )
      return
    }

    setSubmitting(true)

    try {
      const availabilityRequest = getAvailabilityRequest(
        form.equipmentId,
        bookingPayload.startTime,
        bookingPayload.endTime,
      )
      const availability = await api.get(availabilityRequest.url, {
        params: availabilityRequest.params,
      })
      if (!availability.data?.available) {
        const remaining = Number(availability.data?.remainingUnits || 0)
        setError(remaining > 0
          ? 'The equipment is not accepting reservations for this time range.'
          : 'No units are available during this time range. Searching smart alternatives…')
        try {
          setRecommendationsLoading(true)
          const recResp = await api.get('/api/bookings/recommendations', {
            params: {
              equipmentId: form.equipmentId,
              startTime: bookingPayload.startTime,
              endTime: bookingPayload.endTime,
            },
          })
          setRecommendations(recResp.data)
        } catch {
          // ignore error fetching recommendations
        } finally {
          setRecommendationsLoading(false)
        }
        return
      }

      if (recurringBooking) {
        const resp = await api.post('/api/bookings/recurring', {
          equipmentId: Number(form.equipmentId),
          startTime: bookingPayload.startTime,
          endTime: bookingPayload.endTime,
          recurrenceRule: recurringFrequency,
          recurrenceCount: Number(recurringCount),
          purpose: form.purpose,
        })
        setForm(initialForm)
        setRecurringBooking(false)
        setSuccess(resp.data?.summary || 'Recurring bookings submitted successfully.')
        await loadData()
      } else {
        // LocalDateTime has no timezone; preserve the entered lab wall-clock values.
        // The backend validates whether the start time is in its future.
        await api.post('/api/bookings', bookingPayload)

        setForm(initialForm)
        setSuccess('Booking request submitted successfully.')

        await loadData()
      }
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          'Unable to create the booking. Check equipment availability and try again.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleBookingAction(booking, action) {
    if (actionLock.current) return
    actionLock.current = true
    setActionError('')
    setSuccess('')
    setActionInProgress(`${booking.id}:${action}`)

    try {
      await performBookingAction({
        api,
        action,
        bookingId: booking.id,
        refresh: loadData,
        notifyDashboard: () => window.dispatchEvent(new Event(BOOKING_REFRESH_EVENT)),
      })
      const resultText = {
        cancel: 'Booking cancelled.',
        approve: 'Booking approved.',
        reject: 'Booking rejected.',
        'no-show': 'Booking marked as no show.',
        'check-in': 'Check-in successful. Booking is now in use.',
        'check-out': 'Check-out successful. Booking is completed.',
      }[action]
      setSuccess(resultText)
    } catch (err) {
      setActionError(getBookingActionError(err, 'Unable to update this booking.'))
    } finally {
      actionLock.current = false
      setActionInProgress(null)
    }
  }

  const availableStatuses = useMemo(() => {
    const statuses = bookings
      .map((booking) => String(booking.status || 'UNKNOWN').toUpperCase())

    return [...new Set(statuses)].sort()
  }, [bookings])

  const filteredBookings = useMemo(() => {
    const query = search.trim().toLowerCase()

    return bookings.filter((booking) => {
      const matchesSearch = [
        booking.id,
        booking.equipmentId,
        booking.userId,
        booking.purpose,
        booking.status,
      ].some((value) =>
        String(value ?? '').toLowerCase().includes(query),
      )

      const matchesStatus =
        statusFilter === 'ALL' ||
        String(booking.status ?? '').toUpperCase() === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [bookings, search, statusFilter])

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">RESOURCE SCHEDULING</span>
          <h1>Bookings</h1>
          <p>
            Request equipment reservations and review booking records.
          </p>
        </div>

        <div className="module-count">
          <strong>{bookings.length}</strong>
          <span>Total bookings</span>
        </div>
      </section>


      {error && (
        <div className="module-alert" role="alert">
          <span>{error}</span>
          <button
            type="button"
            onClick={loadData}
            disabled={loading || submitting}
          >
            {loading ? 'Retrying…' : 'Retry'}
          </button>
        </div>
      )}

      {success && (
        <div className="auth-success" role="status">
          <span aria-hidden="true">✓</span>
          {success}
        </div>
      )}

      {/* Navigation Tabs */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '24px', borderBottom: '1px solid var(--border)', paddingBottom: '12px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className={`btn ${activeTab === 'bookings' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => setActiveTab('bookings')}
          style={{ fontWeight: 600, fontSize: '14px', padding: '8px 18px' }}
        >
          📅 Bookings & Reservations ({bookings.length})
        </button>

        {canApproveBookings(user?.roles) && (
          <button
            type="button"
            className={`btn ${activeTab === 'approvals' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('approvals')}
            style={{ fontWeight: 600, fontSize: '14px', padding: '8px 18px' }}
          >
            ⏳ Pending Approvals {pendingCount > 0 ? `(${pendingCount})` : ''}
          </button>
        )}

        <button
          type="button"
          className={`btn ${activeTab === 'waitlist' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => setActiveTab('waitlist')}
          style={{ fontWeight: 600, fontSize: '14px', padding: '8px 18px' }}
        >
          📋 My Waitlist {waitlistCount > 0 ? `(${waitlistCount})` : ''}
        </button>
      </div>

      {activeTab === 'approvals' && (
        <PendingApprovalsQueue
          equipmentList={equipment}
          onBookingProcessed={() => {
            loadData()
            loadCounts()
          }}
        />
      )}

      {activeTab === 'waitlist' && (
        <WaitlistPanel
          equipmentList={equipment}
          onWaitlistConverted={() => {
            loadData()
            loadCounts()
          }}
        />
      )}

      {activeTab === 'bookings' && (
        <>
          {canRequestBooking(user?.roles) ? <section className="booking-form-card">
        <div className="module-section-title">
          <div className="module-section-icon" aria-hidden="true">
            📅
          </div>
          <div>
            <h2>Request equipment</h2>
            <p>Choose equipment and specify your required time slot.</p>
          </div>
        </div>

        {loading && equipment.length === 0 ? (
          <p className="booking-form-note">
            Loading equipment options…
          </p>
        ) : equipment.length === 0 ? (
          <p className="booking-form-note">
            No equipment records are available. Add equipment before
            submitting a booking request.
          </p>
        ) : (
          <form className="booking-form" onSubmit={handleSubmit}>
            <div className="form-field">
              <label htmlFor="booking-equipment">Equipment</label>
              <select
                id="booking-equipment"
                name="equipmentId"
                value={form.equipmentId}
                onChange={handleChange}
                required
                disabled={submitting || loading}
              >
                <option value="">Select equipment</option>

                {equipment.map((item) => {
                  const status = String(
                    item.status || 'UNKNOWN',
                  ).toUpperCase()

                  return (
                      <option key={item.id} value={item.id}>
                      {item.name || `Equipment #${item.id}`}
                      {' — '}
                      {status.replaceAll('_', ' ')}
                    </option>
                  )
                })}
              </select>
            </div>

            <div className="booking-time-grid">
              <div className="form-field">
                <label htmlFor="booking-start">Start time</label>
                <input
                  id="booking-start"
                  name="startTime"
                  type="datetime-local"
                  value={form.startTime}
                  onChange={handleChange}
                  required
                  disabled={submitting}
                />
              </div>

              <div className="form-field">
                <label htmlFor="booking-end">End time</label>
                <input
                  id="booking-end"
                  name="endTime"
                  type="datetime-local"
                  value={form.endTime}
                  min={form.startTime || undefined}
                  onChange={handleChange}
                  required
                  disabled={submitting}
                />
              </div>
            </div>

            <div className="form-field">
              <label htmlFor="booking-purpose">Purpose</label>
              <textarea
                id="booking-purpose"
                name="purpose"
                rows={3}
                maxLength={500}
                placeholder="Explain why you need this equipment"
                value={form.purpose}
                onChange={handleChange}
                required
                disabled={submitting}
              />
            </div>

            <div style={{ borderTop: '1px solid var(--border)', paddingTop: '16px', marginTop: '16px', marginBottom: '16px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 600, fontSize: '14px' }}>
                <input
                  type="checkbox"
                  checked={recurringBooking}
                  onChange={(e) => setRecurringBooking(e.target.checked)}
                  disabled={submitting}
                />
                🔁 Repeat as Recurring Booking
              </label>

              {recurringBooking && (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginTop: '12px', padding: '14px', background: 'var(--surface-secondary, #fafafa)', borderRadius: '8px', border: '1px solid var(--border)' }}>
                  <div className="form-field">
                    <label htmlFor="recurring-frequency">Frequency Pattern</label>
                    <select
                      id="recurring-frequency"
                      value={recurringFrequency}
                      onChange={(e) => {
                        setRecurringFrequency(e.target.value)
                        setRecurringPreview(null)
                      }}
                      disabled={submitting}
                    >
                      <option value="DAILY">Daily</option>
                      <option value="WEEKLY">Weekly</option>
                      <option value="BIWEEKLY">Bi-weekly (Every 2 weeks)</option>
                      <option value="MONTHLY">Monthly</option>
                    </select>
                  </div>
                  <div className="form-field">
                    <label htmlFor="recurring-count">Number of Occurrences (2-12)</label>
                    <input
                      id="recurring-count"
                      type="number"
                      min={2}
                      max={12}
                      value={recurringCount}
                      onChange={(e) => {
                        setRecurringCount(Math.max(2, Math.min(12, parseInt(e.target.value) || 2)))
                        setRecurringPreview(null)
                      }}
                      disabled={submitting}
                    />
                  </div>
                  <div style={{ gridColumn: '1 / -1', display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="button button-secondary"
                      onClick={handlePreviewRecurring}
                      disabled={submitting || previewLoading}
                    >
                      {previewLoading ? 'Checking availability…' : '👁 Preview Recurrence Schedule'}
                    </button>
                    {recurringPreview && (
                      <span style={{ fontSize: '13px', color: recurringPreview.conflictCount > 0 ? '#f59e0b' : '#10b981', fontWeight: 600 }}>
                        {recurringPreview.summary}
                      </span>
                    )}
                  </div>
                  {recurringPreview?.createdBookings?.length > 0 && (
                    <div style={{ gridColumn: '1 / -1', marginTop: '8px' }}>
                      <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left' }}>
                            <th style={{ padding: '6px' }}>#</th>
                            <th style={{ padding: '6px' }}>Start Time</th>
                            <th style={{ padding: '6px' }}>End Time</th>
                            <th style={{ padding: '6px' }}>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {recurringPreview.createdBookings.map((occ, idx) => (
                            <tr key={idx} style={{ borderBottom: '1px solid var(--border)' }}>
                              <td style={{ padding: '6px' }}>{idx + 1}</td>
                              <td style={{ padding: '6px' }}>{formatDate(occ.startTime)}</td>
                              <td style={{ padding: '6px' }}>{formatDate(occ.endTime)}</td>
                              <td style={{ padding: '6px', color: '#10b981', fontWeight: 600 }}>✓ Available</td>
                            </tr>
                          ))}
                          {recurringPreview.conflictedSlots?.map((cSlot, cIdx) => (
                            <tr key={`conf-${cIdx}`} style={{ borderBottom: '1px solid var(--border)', background: 'rgba(239, 68, 68, 0.05)' }}>
                              <td style={{ padding: '6px' }}>—</td>
                              <td style={{ padding: '6px' }}>{formatDate(cSlot)}</td>
                              <td style={{ padding: '6px' }}>—</td>
                              <td style={{ padding: '6px', color: '#ef4444', fontWeight: 600 }}>✕ Conflicted</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>

            <button
              type="submit"
              className="button button-primary"
              disabled={submitting || loading || equipment.length === 0}
            >
              {submitting ? 'Submitting request…' : 'Submit booking request'}
            </button>
          </form>
        )}

        {recommendations && (
          <SchedulingRecommendations
            recommendations={recommendations}
            loading={recommendationsLoading}
            onSelectTimeSlot={handleSelectRecommendedSlot}
            onSelectEquipment={handleSelectRecommendedEquipment}
            onJoinWaitlist={handleJoinWaitlistFromRecommendation}
            onClose={() => setRecommendations(null)}
          />
        )}

        <p className="booking-form-note">
          Availability is checked before submission and rechecked by the backend under a per-equipment lock. Each booking request reserves one unit. New requests use PENDING status and require approval.
        </p>
      </section> : (
        <div className="auth-success" role="status">
          Booking requests can be submitted by Researchers/Students. You can review the bookings available to your role below.
        </div>
      )}

      {actionError && (
        <div className="module-alert" role="alert">{actionError}</div>
      )}

      <section className="booking-list-section">
        <div className="dashboard-section-heading">
          <div>
            <span className="eyebrow">RESERVATION HISTORY</span>
            <h2>Booking records</h2>
          </div>
        </div>

        <div className="module-toolbar">
          <div className="module-search">
            <span aria-hidden="true">🔍</span>
            <input
              type="search"
              aria-label="Search bookings"
              placeholder="Search by ID, equipment, user, purpose, or status"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>

          <select
            className="module-select"
            aria-label="Filter booking status"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="ALL">All statuses</option>

            {availableStatuses.map((status) => (
              <option key={status} value={status}>
                {status.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </div>

        {loading ? (
          <div className="module-state" role="status">
            <span className="loading-spinner" />
            <p>Loading bookings…</p>
          </div>
        ) : error && bookings.length === 0 ? (
          <div className="module-state">
            <h2>Booking records unavailable</h2>
            <p>Use Retry above to request the data again.</p>
          </div>
        ) : filteredBookings.length === 0 ? (
          <div className="module-state">
            <div className="module-empty-icon" aria-hidden="true">
              📅
            </div>
            <h2>No bookings found</h2>
            <p>
              {bookings.length === 0
                ? 'No booking records are currently available.'
                : 'Try changing your search or status filter.'}
            </p>
          </div>
        ) : (
          <>
            <p className="module-results-count">
              Showing {filteredBookings.length} of {bookings.length} bookings
            </p>

            <div className="booking-table-wrapper">
              <table className="booking-table">
                <thead>
                  <tr>
                    <th>Booking</th>
                    <th>Equipment</th>
                    <th>Schedule</th>
                    <th>Purpose</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>

                <tbody>
                  {filteredBookings.map((booking) => {
                    const actions = getBookingActions(booking, user?.roles, user?.id)
                    const checkIn = getCheckInAvailability(booking, user?.id)
                    const pendingAction = actionInProgress?.startsWith(`${booking.id}:`)
                    return (
                    <tr key={booking.id}>
                      <td><Link to={`/bookings/${booking.id}`}>#{booking.id}</Link></td>
                      <td>#{booking.equipmentId ?? '—'}</td>
                      <td>
                        <div className="booking-schedule">
                          <span>{formatDate(booking.startTime)}</span>
                          <small>to {formatDate(booking.endTime)}</small>
                        </div>
                      </td>
                      <td>{booking.purpose || '—'}</td>
                      <td>
                        <span className={getStatusClass(booking.status)}>
                          {formatBookingStatus(booking.status)}
                        </span>
                      </td>
                      <td>
                        {actions.includes('cancel') && (
                          <>
                            <button type="button" className="secondary-button" disabled={Boolean(actionInProgress)} onClick={() => handleBookingAction(booking, 'cancel')}>
                              {pendingAction && actionInProgress.endsWith(':cancel') ? 'Cancelling…' : 'Cancel'}
                            </button>
                            {booking.purpose?.includes('Recurring') && (
                              <>
                                {' '}
                                <button
                                  type="button"
                                  className="secondary-button"
                                  style={{ color: '#ef4444' }}
                                  disabled={Boolean(actionInProgress)}
                                  onClick={() => handleCancelSeries(booking)}
                                >
                                  {actionInProgress === `${booking.id}:cancel-series` ? 'Cancelling series…' : 'Cancel Series'}
                                </button>
                              </>
                            )}
                          </>
                        )}
                        {actions.includes('check-in') && (
                          <button type="button" className="secondary-button" disabled={Boolean(actionInProgress) || !checkIn.eligible} title={checkIn.reason || undefined} onClick={() => handleBookingAction(booking, 'check-in')}>
                            {pendingAction && actionInProgress.endsWith(':check-in') ? 'Checking in…' : 'Check In'}
                          </button>
                        )}
                        {actions.includes('check-out') && (
                          <button type="button" className="secondary-button" disabled={Boolean(actionInProgress)} onClick={() => handleBookingAction(booking, 'check-out')}>
                            {pendingAction && actionInProgress.endsWith(':check-out') ? 'Checking out…' : 'Check Out'}
                          </button>
                        )}
                        {actions.includes('approve') && (
                          <>
                            <button type="button" className="secondary-button" disabled={Boolean(actionInProgress)} onClick={() => handleBookingAction(booking, 'approve')}>
                              {pendingAction && actionInProgress.endsWith(':approve') ? 'Approving…' : 'Approve'}
                            </button>{' '}
                            <button type="button" className="secondary-button" disabled={Boolean(actionInProgress)} onClick={() => handleBookingAction(booking, 'reject')}>
                              {pendingAction && actionInProgress.endsWith(':reject') ? 'Rejecting…' : 'Reject'}
                            </button>
                          </>
                        )}
                        {actions.includes('no-show') && (
                          <button type="button" className="secondary-button" disabled={Boolean(actionInProgress)} onClick={() => handleBookingAction(booking, 'no-show')}>
                            {pendingAction && actionInProgress.endsWith(':no-show') ? 'Updating…' : 'Mark no show'}
                          </button>
                        )}
                        {!actions.length && <span aria-label="No available actions">—</span>}
                        {' '}
                        <button type="button" className="secondary-button" onClick={() => viewAudit(booking.id)}>
                          Audit
                        </button>
                      </td>
                    </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>

      {selectedAuditBookingId != null && (
        <section className="booking-form-card" style={{ marginTop: '1.5rem' }}>
          <div className="module-section-title">
            <div>
              <h2>Audit Trail for Booking #{selectedAuditBookingId}</h2>
              <p>Chronological history of lifecycle transitions and events.</p>
            </div>
            <button type="button" className="secondary-button" onClick={() => setSelectedAuditBookingId(null)}>
              Close audit
            </button>
          </div>
          {auditLoading ? (
            <p role="status">Loading audit history…</p>
          ) : auditError ? (
            <div className="module-alert" role="alert">{auditError}</div>
          ) : auditLogs.length === 0 ? (
            <p>No audit events recorded for this booking.</p>
          ) : (
            <div className="booking-table-wrapper">
              <table className="booking-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Event</th>
                    <th>Transition</th>
                    <th>Actor</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.map((log) => (
                    <tr key={log.id}>
                      <td>{formatDate(log.occurredAt)}</td>
                      <td><strong>{log.eventType}</strong></td>
                      <td>{log.previousStatus ? `${log.previousStatus} → ` : ''}{log.newStatus || '—'}</td>
                      <td>{log.actorEmail || 'System'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
        </>
      )}
    </div>
  )
}
