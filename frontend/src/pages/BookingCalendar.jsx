import { useEffect, useMemo, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/react/daygrid'
import timeGridPlugin from '@fullcalendar/react/timegrid'
import '@fullcalendar/react/skeleton.css'
import api from '../api/axiosInstance'
import { getBookingRecordsUrl } from '../access/roleAccess.js'
import { toBookingCalendarEvents } from '../access/bookingCalendar.js'
import { useAuth } from '../context/AuthContext'

const EMPTY_BOOKINGS = Object.freeze([])

function getErrorMessage(error) {
  if (error?.response?.status === 403) {
    return 'Access denied. Your role or institution scope cannot view these bookings.'
  }
  return error?.response?.data?.message || error?.response?.data?.error || error?.message || 'Unable to load the booking schedule.'
}

export default function BookingCalendar() {
  const { user } = useAuth()
  const endpoint = useMemo(
    () => getBookingRecordsUrl(user?.roles, user?.id),
    [user?.id, user?.roles],
  )
  const requestKey = `${user?.id ?? ''}:${endpoint ?? ''}`
  const [result, setResult] = useState({ key: '', bookings: [], error: '' })
  const [selectedEvent, setSelectedEvent] = useState(null)
  const loading = result.key !== requestKey
  const bookings = loading ? EMPTY_BOOKINGS : result.bookings
  const error = loading ? '' : result.error
  const events = useMemo(() => toBookingCalendarEvents(bookings), [bookings])

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    async function loadBookings() {
      if (!endpoint) {
        setResult({ key: requestKey, bookings: [], error: 'Booking history is unavailable for this account.' })
        return
      }
      try {
        const response = await api.get(endpoint, { signal: controller.signal })
        if (active) {
          setResult({
            key: requestKey,
            bookings: Array.isArray(response.data) ? response.data : [],
            error: '',
          })
        }
      } catch (requestError) {
        if (active && requestError.name !== 'CanceledError') {
          setResult({ key: requestKey, bookings: [], error: getErrorMessage(requestError) })
        }
      }
    }

    loadBookings()
    return () => {
      active = false
      controller.abort()
    }
  }, [endpoint, requestKey])

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">RESERVATION SCHEDULE</span>
          <h1>Booking calendar</h1>
          <p>Bookings visible to your account are shown in the laboratory’s local date and time.</p>
        </div>
      </section>

      <div className="auth-success" role="note">
        This calendar displays existing booking records. It does not show free slots or guarantee availability; the backend checks conflicts when a request is submitted.
      </div>

      {loading ? (
        <div className="module-state" role="status"><span className="loading-spinner" /><p>Loading booking calendar…</p></div>
      ) : error ? (
        <div className="module-state" role="alert"><h2>Booking schedule unavailable</h2><p>{error}</p></div>
      ) : events.length === 0 ? (
        <div className="module-state"><h2>No bookings to display</h2><p>Your authorized booking collection is empty.</p></div>
      ) : (
        <section className="booking-form-card booking-calendar" aria-label="Booking schedule calendar">
          <FullCalendar
            plugins={[dayGridPlugin, timeGridPlugin]}
            initialView="dayGridMonth"
            headerToolbar={{ left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay' }}
            events={events}
            eventTimeFormat={{ hour: 'numeric', minute: '2-digit', hour12: true }}
            eventClick={({ event }) => setSelectedEvent({
              title: event.title,
              start: event.start?.toLocaleString() || 'Unavailable',
              end: event.end?.toLocaleString() || 'Unavailable',
              purpose: event.extendedProps.purpose,
            })}
            height="auto"
          />
          {selectedEvent && (
            <div className="calendar-event-details" role="status">
              <strong>{selectedEvent.title}</strong>
              <p>{selectedEvent.start} – {selectedEvent.end}</p>
              {selectedEvent.purpose && <p>{selectedEvent.purpose}</p>}
            </div>
          )}
        </section>
      )}
    </div>
  )
}
