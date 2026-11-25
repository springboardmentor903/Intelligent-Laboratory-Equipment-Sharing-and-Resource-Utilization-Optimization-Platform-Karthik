import test from 'node:test'
import assert from 'node:assert/strict'
import {
  NOTIFICATION_TYPES,
  formatUnreadCount,
  isCriticalAlert,
  getNotificationIcon,
  getNotificationRoute,
  formatNotificationTime,
} from '../src/access/notificationWorkflow.js'

test('NOTIFICATION_TYPES defines all expected platform notification event types', () => {
  assert.ok(Array.isArray(NOTIFICATION_TYPES))
  assert.ok(NOTIFICATION_TYPES.includes('BOOKING_CONFIRMED'))
  assert.ok(NOTIFICATION_TYPES.includes('BOOKING_REJECTED'))
  assert.ok(NOTIFICATION_TYPES.includes('BOOKING_CANCELLED'))
  assert.ok(NOTIFICATION_TYPES.includes('BOOKING_REMINDER'))
  assert.ok(NOTIFICATION_TYPES.includes('WAITLIST_OFFER'))
  assert.ok(NOTIFICATION_TYPES.includes('PREVENTIVE_MAINTENANCE_CREATED'))
  assert.ok(NOTIFICATION_TYPES.includes('CALIBRATION_WORK_ORDER_CREATED'))
  assert.ok(NOTIFICATION_TYPES.includes('CALIBRATION_EXPIRED'))
  assert.ok(NOTIFICATION_TYPES.includes('IDLE_EQUIPMENT_ALERT'))
  assert.ok(NOTIFICATION_TYPES.includes('SHARING_REQUEST_SUBMITTED'))
  assert.ok(NOTIFICATION_TYPES.includes('SHARING_REQUEST_FORWARDED'))
  assert.ok(NOTIFICATION_TYPES.includes('SHARING_REQUEST_APPROVED'))
  assert.ok(NOTIFICATION_TYPES.includes('SHARING_REQUEST_REJECTED'))
  assert.ok(NOTIFICATION_TYPES.includes('SHARING_AGREEMENT_REVOKED'))
})

test('formatUnreadCount returns correct badges and handles 99+ ceiling', () => {
  assert.equal(formatUnreadCount(0), null)
  assert.equal(formatUnreadCount(-5), null)
  assert.equal(formatUnreadCount(null), null)
  assert.equal(formatUnreadCount(undefined), null)
  assert.equal(formatUnreadCount(1), '1')
  assert.equal(formatUnreadCount(99), '99')
  assert.equal(formatUnreadCount(100), '99+')
  assert.equal(formatUnreadCount(150), '99+')
})

test('isCriticalAlert identifies safety/urgent notifications', () => {
  assert.equal(isCriticalAlert({ notificationType: 'CALIBRATION_EXPIRED' }), true)
  assert.equal(isCriticalAlert({ notificationType: 'WAITLIST_OFFER' }), true)
  assert.equal(
    isCriticalAlert({
      notificationType: 'BOOKING_CANCELLED',
      message: 'Cancelled due to urgent maintenance work order',
    }),
    true
  )
  assert.equal(
    isCriticalAlert({
      notificationType: 'BOOKING_CANCELLED',
      message: 'User requested cancellation',
    }),
    false
  )
  assert.equal(isCriticalAlert({ notificationType: 'BOOKING_CONFIRMED' }), false)
  assert.equal(isCriticalAlert(null), false)
})

test('getNotificationIcon provides appropriate emoji for notification types', () => {
  assert.equal(getNotificationIcon('BOOKING_CONFIRMED'), '✅')
  assert.equal(getNotificationIcon('BOOKING_REJECTED'), '❌')
  assert.equal(getNotificationIcon('BOOKING_CANCELLED'), '🚫')
  assert.equal(getNotificationIcon('BOOKING_REMINDER'), '⏰')
  assert.equal(getNotificationIcon('WAITLIST_OFFER'), '🎟️')
  assert.equal(getNotificationIcon('PREVENTIVE_MAINTENANCE_CREATED'), '🛠️')
  assert.equal(getNotificationIcon('CALIBRATION_WORK_ORDER_CREATED'), '📐')
  assert.equal(getNotificationIcon('CALIBRATION_EXPIRED'), '⚠️')
  assert.equal(getNotificationIcon('IDLE_EQUIPMENT_ALERT'), '💤')
  assert.equal(getNotificationIcon('SHARING_REQUEST_SUBMITTED'), '📤')
  assert.equal(getNotificationIcon('SHARING_REQUEST_APPROVED'), '🤝')
  assert.equal(getNotificationIcon('UNKNOWN_TYPE'), '🔔')
})

test('getNotificationRoute maps reference types and notification types to platform routes', () => {
  assert.equal(
    getNotificationRoute({ referenceType: 'BOOKING', referenceId: '10' }),
    '/bookings'
  )
  assert.equal(getNotificationRoute({ referenceType: 'WAITLIST' }), '/bookings')
  assert.equal(getNotificationRoute({ referenceType: 'WORK_ORDER' }), '/maintenance')
  assert.equal(getNotificationRoute({ referenceType: 'CALIBRATION' }), '/calibration')
  assert.equal(getNotificationRoute({ referenceType: 'EQUIPMENT' }), '/equipment')
  assert.equal(getNotificationRoute({ referenceType: 'SHARING_REQUEST' }), '/sharing')
  assert.equal(getNotificationRoute({ referenceType: 'SHARING_AGREEMENT' }), '/sharing')

  // Fallbacks
  assert.equal(getNotificationRoute({ notificationType: 'BOOKING_REMINDER' }), '/bookings')
  assert.equal(getNotificationRoute({ notificationType: 'CALIBRATION_EXPIRED' }), '/calibration')
  assert.equal(getNotificationRoute({ notificationType: 'PREVENTIVE_MAINTENANCE_CREATED' }), '/maintenance')
  assert.equal(getNotificationRoute({ notificationType: 'IDLE_EQUIPMENT_ALERT' }), '/equipment')
  assert.equal(getNotificationRoute({ notificationType: 'SHARING_REQUEST_APPROVED' }), '/sharing')
  assert.equal(getNotificationRoute(null), '/notifications')
})

test('formatNotificationTime handles recent and formatted timestamps', () => {
  assert.equal(formatNotificationTime(null), '—')
  const now = new Date().toISOString()
  assert.equal(formatNotificationTime(now), 'Just now')

  const tenMinsAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString()
  assert.equal(formatNotificationTime(tenMinsAgo), '10m ago')

  const twoHoursAgo = new Date(Date.now() - 2 * 3600 * 1000).toISOString()
  assert.equal(formatNotificationTime(twoHoursAgo), '2h ago')
})
