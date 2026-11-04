import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { getBookingActionError, getBookingActions, getCheckInAvailability, performBookingAction, formatBookingStatus } from '../access/bookingWorkflow.js'

function formatDate(value) {
  if (!value) return 'Not provided'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? 'Not provided' : parsed.toLocaleString()
}

export default function BookingDetails() {
  const { id } = useParams()
  const { user } = useAuth()
  const [booking, setBooking] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')
  const lock = useRef(false)

  const reload = useCallback(async () => {
    setLoading(true); setError('')
    try { const response = await api.get(`/api/bookings/${id}`); setBooking(response.data) }
    catch (requestError) {
      if (requestError?.response?.status === 403) setError('You do not have permission to view this booking.')
      else if (requestError?.response?.status === 404) setError('This booking was not found or is no longer available to you.')
      else setError(requestError?.response?.data?.message || 'Unable to load this booking.')
    } finally { setLoading(false) }
  }, [id])

  useEffect(() => { void reload() }, [reload])

  async function runAction(action) {
    if (!booking || lock.current) return
    lock.current = true; setBusy(action); setError(''); setNotice('')
    try {
      await performBookingAction({ api, action, bookingId: booking.id, refresh: reload })
      setNotice(action === 'check-in' ? 'Check-in successful. Booking is now in use.' : 'Check-out successful. Booking is completed.')
    } catch (actionError) { setError(getBookingActionError(actionError, 'Unable to update this booking.')) }
    finally { lock.current = false; setBusy('') }
  }

  const actions = booking ? getBookingActions(booking, user?.roles, user?.id) : []
  const checkIn = booking ? getCheckInAvailability(booking, user?.id) : { visible: false, eligible: false }

  return <section className="module-page">
    <p><Link to="/bookings">← Back to bookings</Link></p>
    <header className="module-page-heading"><div><span className="eyebrow">RESERVATION DETAILS</span><h1>Booking #{id}</h1><p>View the booking information and available owner actions.</p></div><button type="button" className="secondary-button" onClick={reload} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button></header>
    {notice && <div className="auth-success" role="status">{notice}</div>}
    {error && <div className="module-alert" role="alert">{error}</div>}
    {loading && !booking ? <div className="module-state" role="status"><span className="loading-spinner"/><p>Loading booking…</p></div>
      : booking && <article className="booking-form-card">
        <h2>{booking.equipmentName || `Equipment #${booking.equipmentId}`}</h2>
        <dl className="monitoring-details">
          <div><dt>Booking ID</dt><dd>{booking.id}</dd></div><div><dt>Status</dt><dd>{formatBookingStatus(booking.status)}</dd></div>
          <div><dt>Start</dt><dd>{formatDate(booking.startTime)}</dd></div><div><dt>End</dt><dd>{formatDate(booking.endTime)}</dd></div>
          <div><dt>Purpose</dt><dd>{booking.purpose || 'Not provided'}</dd></div>
        </dl>
        <div className="booking-detail-actions">
          {checkIn.visible && <button type="button" className="button button-primary" disabled={Boolean(busy) || loading || !checkIn.eligible} title={checkIn.reason} onClick={() => runAction('check-in')}>{busy === 'check-in' ? 'Checking in…' : 'Check In'}</button>}
          {actions.includes('check-out') && <button type="button" className="button button-primary" disabled={Boolean(busy) || loading} onClick={() => runAction('check-out')}>{busy === 'check-out' ? 'Checking out…' : 'Check Out'}</button>}
          {checkIn.visible && !checkIn.eligible && <p>{checkIn.reason}</p>}
        </div>
      </article>}
  </section>
}
