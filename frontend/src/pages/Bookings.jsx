
import { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

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
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const results = await Promise.allSettled([
        api.get('/api/bookings'),
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
    } catch (err) {
      setError(
        getErrorMessage(err, 'Unable to load booking information.'),
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
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
      return
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

    const start = new Date(form.startTime)
    const end = new Date(form.endTime)

    if (Number.isNaN(start.getTime()) || start <= new Date()) {
      setError('The booking start time must be in the future.')
      return
    }

    if (Number.isNaN(end.getTime()) || end <= start) {
      setError('The end time must be after the start time.')
      return
    }

    if (
      selectedEquipment.status &&
      !['AVAILABLE'].includes(
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
      await api.post('/api/bookings', {
        equipmentId: Number(form.equipmentId),
        userId: Number(user.id),
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        purpose: form.purpose.trim(),
      })

      setForm(initialForm)
      setSuccess('Booking request submitted successfully.')

      await loadData()
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

      <section className="booking-form-card">
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
                  min={new Date(Date.now() + 60000)
                    .toISOString()
                    .slice(0, 16)}
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
                maxLength={1000}
                placeholder="Explain why you need this equipment"
                value={form.purpose}
                onChange={handleChange}
                required
                disabled={submitting}
              />
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

        <p className="booking-form-note">
          Requests are submitted with PENDING status, subject to backend
          validation and approval.
        </p>
      </section>

      <section className="booking-list-section">
        <div className="dashboard-section-heading">
          <div>
            <span className="eyebrow">RESERVATION HISTORY</span>
            <h2>Booking records</h2>
          </div>
        </div>

        <div className="module-toolbar">
          <div className="module-search">
            <span aria-hidden="true">⌕</span>
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
                  </tr>
                </thead>

                <tbody>
                  {filteredBookings.map((booking) => (
                    <tr key={booking.id}>
                      <td>#{booking.id}</td>
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
                          {String(booking.status || 'UNKNOWN').replaceAll(
                            '_',
                            ' ',
                          )}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
