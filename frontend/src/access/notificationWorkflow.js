export const NOTIFICATION_TYPES = Object.freeze([
  'BOOKING_CONFIRMED',
  'BOOKING_REJECTED',
  'BOOKING_CANCELLED',
  'BOOKING_REMINDER',
  'WAITLIST_OFFER',
  'PREVENTIVE_MAINTENANCE_CREATED',
  'CALIBRATION_WORK_ORDER_CREATED',
  'CALIBRATION_EXPIRED',
  'IDLE_EQUIPMENT_ALERT',
  'SHARING_REQUEST_SUBMITTED',
  'SHARING_REQUEST_FORWARDED',
  'SHARING_REQUEST_APPROVED',
  'SHARING_REQUEST_REJECTED',
  'SHARING_AGREEMENT_REVOKED',
])

export function formatUnreadCount(count) {
  const num = Number(count || 0)
  if (num <= 0) return null
  if (num > 99) return '99+'
  return String(num)
}

export function isCriticalAlert(notification) {
  if (!notification) return false
  const type = notification.notificationType
  if (type === 'CALIBRATION_EXPIRED' || type === 'WAITLIST_OFFER') {
    return true
  }
  if (type === 'BOOKING_CANCELLED') {
    const msg = (notification.message || '').toLowerCase()
    const title = (notification.title || '').toLowerCase()
    return msg.includes('maintenance') || title.includes('maintenance')
  }
  return false
}

export function getNotificationIcon(type) {
  switch (type) {
    case 'BOOKING_CONFIRMED':
      return '✅'
    case 'BOOKING_REJECTED':
      return '❌'
    case 'BOOKING_CANCELLED':
      return '🚫'
    case 'BOOKING_REMINDER':
      return '⏰'
    case 'WAITLIST_OFFER':
      return '🎟️'
    case 'PREVENTIVE_MAINTENANCE_CREATED':
      return '🛠️'
    case 'CALIBRATION_WORK_ORDER_CREATED':
      return '📐'
    case 'CALIBRATION_EXPIRED':
      return '⚠️'
    case 'IDLE_EQUIPMENT_ALERT':
      return '💤'
    case 'SHARING_REQUEST_SUBMITTED':
    case 'SHARING_REQUEST_FORWARDED':
      return '📤'
    case 'SHARING_REQUEST_APPROVED':
      return '🤝'
    case 'SHARING_REQUEST_REJECTED':
      return '🛑'
    case 'SHARING_AGREEMENT_REVOKED':
      return '🔒'
    default:
      return '🔔'
  }
}

export function getNotificationRoute(notification) {
  if (!notification) return '/notifications'

  const refType = notification.referenceType
  const refId = notification.referenceId

  if (refType === 'BOOKING') {
    return refId ? `/bookings` : '/bookings'
  }
  if (refType === 'WAITLIST') {
    return '/bookings'
  }
  if (refType === 'WORK_ORDER') {
    return '/maintenance'
  }
  if (refType === 'CALIBRATION') {
    return '/calibration'
  }
  if (refType === 'EQUIPMENT') {
    return '/equipment'
  }
  if (refType === 'SHARING_REQUEST' || refType === 'SHARING_AGREEMENT') {
    return '/sharing'
  }

  // Fallback by notification type
  const type = notification.notificationType
  if (type?.startsWith('BOOKING') || type === 'WAITLIST_OFFER') {
    return '/bookings'
  }
  if (type === 'CALIBRATION_EXPIRED' || type === 'CALIBRATION_WORK_ORDER_CREATED') {
    return '/calibration'
  }
  if (type === 'PREVENTIVE_MAINTENANCE_CREATED') {
    return '/maintenance'
  }
  if (type === 'IDLE_EQUIPMENT_ALERT') {
    return '/equipment'
  }
  if (type?.startsWith('SHARING')) {
    return '/sharing'
  }

  return '/notifications'
}

export function formatNotificationTime(timestamp) {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  if (isNaN(date.getTime())) return String(timestamp)

  const now = new Date()
  const diffSec = Math.floor((now - date) / 1000)

  if (diffSec < 60) return 'Just now'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`

  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
