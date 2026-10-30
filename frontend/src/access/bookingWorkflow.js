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

export function getBookingActions(booking, roles, authenticatedUserId) {
  if (!booking?.id || !hasValidRoles(roles)) return []

  const status = String(booking.status || '').toUpperCase()
  const actions = []

  if (
    roles.includes('RESEARCHER_STUDENT') &&
    authenticatedUserId != null &&
    String(booking.userId) === String(authenticatedUserId) &&
    CANCELLABLE_STATUSES.includes(status)
  ) {
    actions.push('cancel')
  }

  if (STAFF_DECISION_ROLES.some((role) => roles.includes(role))) {
    if (status === 'PENDING') actions.push('approve', 'reject')
    if (status === 'APPROVED') actions.push('start', 'no-show')
    if (status === 'IN_USE') actions.push('complete')
  }

  return actions
}

export function getBookingActionRequest(action, bookingId) {
  if (action === 'cancel') {
    return { url: `/api/bookings/${bookingId}/cancel`, data: undefined }
  }
  if (['approve', 'reject', 'start', 'complete', 'no-show'].includes(action)) {
    const status = {
      approve: 'APPROVED',
      reject: 'REJECTED',
      start: 'IN_USE',
      complete: 'COMPLETED',
      'no-show': 'NO_SHOW',
    }[action]
    return {
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
  await api.patch(request.url, request.data)
  await refresh()
  notifyDashboard?.()
}

export function getBookingActionError(error, fallback) {
  if (error?.response?.status === 403) {
    return 'Access denied. Your role or account scope does not permit this booking action.'
  }
  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    fallback
  )
}
