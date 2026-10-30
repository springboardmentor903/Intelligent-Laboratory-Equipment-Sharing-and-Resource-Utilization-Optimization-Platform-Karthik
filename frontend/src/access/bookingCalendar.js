import { formatBookingStatus } from './bookingWorkflow.js'

export function toBookingCalendarEvents(bookings) {
  if (!Array.isArray(bookings)) return []

  return bookings
    .filter((booking) => booking?.startTime && booking?.endTime)
    .map((booking) => {
      const status = String(booking.status || 'UNKNOWN').toUpperCase()
      return {
        id: String(booking.id),
        title: `Equipment #${booking.equipmentId ?? '—'} · ${formatBookingStatus(status)}`,
        start: booking.startTime,
        end: booking.endTime,
        extendedProps: {
          bookingId: booking.id,
          equipmentId: booking.equipmentId,
          status,
          purpose: booking.purpose || '',
        },
      }
    })
}
