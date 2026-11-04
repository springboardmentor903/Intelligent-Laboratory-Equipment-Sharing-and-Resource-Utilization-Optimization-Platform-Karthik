import { hasValidRoles } from './roleAccess.js'

const STAFF_DECISION_ROLES = Object.freeze([
  'LAB_MANAGER',
  'INSTITUTION_ADMIN',
  'SYSTEM_ADMIN',
])
const CANCELLABLE_STATUSES = Object.freeze(['PENDING', 'APPROVED'])
const BOOKING_STATUS_LABELS = Object.freeze({
  PENDING: 'Pending Approval',
  APPROVED: 'Confirmed',
  CONFIRMED: 'Confirmed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  IN_USE: 'In Use',
  COMPLETED: 'Completed',
  NO_SHOW: 'No Show',
})

export const BOOKING_REFRESH_EVENT = 'lab-bookings-updated'

export function formatBookingStatus(status) {
  const normalized = String(status || 'UNKNOWN').toUpperCase()
  return BOOKING_STATUS_LABELS[normalized] || normalized.replaceAll('_', ' ')
}

function isBookingOwner(booking, authenticatedUserId) {
  return authenticatedUserId != null
    && booking?.userId != null
    && String(booking.userId) === String(authenticatedUserId)
}

function localDateTimeMs(value) {
  if (typeof value !== 'string') return Number.NaN
  const normalized = value.trim().replace(' ', 'T')
  const match = /^(\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d(?:\.\d{1,9})?)?)$/.exec(normalized)
  if (!match) return Number.NaN
  return new Date(match[1]).getTime()
}

export function getCheckInAvailability(booking, authenticatedUserId, now = Date.now()) {
  const status = String(booking?.status || '').toUpperCase()
  if (!isBookingOwner(booking, authenticatedUserId)
      || !['APPROVED', 'CONFIRMED'].includes(status)) {
    return { visible: false, eligible: false, reason: '' }
  }
  const start = localDateTimeMs(booking.startTime)
  const end = localDateTimeMs(booking.endTime)
  const current = now instanceof Date ? now.getTime() : Number(now)
  if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(current)) {
    return { visible: true, eligible: false, reason: 'Booking time is unavailable.' }
  }
  if (current < start - 15 * 60 * 1000) {
    return { visible: true, eligible: false, reason: 'Check-in opens 15 minutes before the booking.' }
  }
  if (current > end) {
    return { visible: true, eligible: false, reason: 'The scheduled booking has ended.' }
  }
  return { visible: true, eligible: true, reason: '' }
}

export function getBookingActions(booking, roles, authenticatedUserId) {
  if (!booking?.id || !hasValidRoles(roles)) return []

  const status = String(booking.status || '').toUpperCase()
  const actions = []
  const owner = isBookingOwner(booking, authenticatedUserId)

  if (
    roles.some((role) => ['RESEARCHER_STUDENT', 'DEPARTMENT_HEAD'].includes(role)) &&
    owner &&
    CANCELLABLE_STATUSES.includes(status)
  ) {
    actions.push('cancel')
  }

  if (owner && ['APPROVED', 'CONFIRMED'].includes(status)) actions.push('check-in')
  if (owner && status === 'IN_USE') actions.push('check-out')

  if (STAFF_DECISION_ROLES.some((role) => roles.includes(role))) {
    if (status === 'PENDING') actions.push('approve', 'reject')
    if (['APPROVED', 'CONFIRMED'].includes(status)) actions.push('no-show')
  }

  return actions
}

export function getBookingActionRequest(action, bookingId) {
  if (action === 'check-in' || action === 'check-out') {
    return {
      method: 'post',
      url: `/api/bookings/${bookingId}/${action}`,
      data: undefined,
    }
  }
  if (action === 'cancel') {
    return { method: 'patch', url: `/api/bookings/${bookingId}/cancel`, data: undefined }
  }
  if (['approve', 'reject', 'no-show'].includes(action)) {
    const status = {
      approve: 'APPROVED',
      reject: 'REJECTED',
      'no-show': 'NO_SHOW',
    }[action]
    return {
      method: 'patch',
      url: `/api/bookings/${bookingId}/status`,
      data: { status },
    }
  }
  throw new TypeError(`Unsupported booking action: ${action}`)
}

export function getAvailabilityRequest(equipmentId, startTime, endTime) {
  if (!equipmentId || !startTime || !endTime || endTime <= startTime) {
    throw new TypeError('Equipment and a valid booking time range are required')
  }
  return {
    url: '/api/bookings/availability',
    params: { equipmentId, startTime, endTime },
  }
}

export async function performBookingAction({
  api,
  action,
  bookingId,
  refresh,
  notifyDashboard,
}) {
  const request = getBookingActionRequest(action, bookingId)
  await api[request.method](request.url, request.data)
  await refresh()
  notifyDashboard?.()
}

export function getBookingActionError(error, fallback) {
  const status = error?.response?.status
  if (status === 400) return error?.response?.data?.message || error?.response?.data?.error || 'The request could not be processed.'
  if (status === 403) {
    return 'Access denied. Your role or account scope does not permit this booking action.'
  }
  if (status === 404) return 'This booking could not be found or is no longer available to you.'
  if (status === 409) return error?.response?.data?.message || error?.response?.data?.detail || 'This booking has changed and can no longer be updated.'
  return (
    error?.response?.data?.message ||
    error?.response?.data?.detail ||
    error?.response?.data?.error ||
    error?.message ||
    fallback
  )
}
