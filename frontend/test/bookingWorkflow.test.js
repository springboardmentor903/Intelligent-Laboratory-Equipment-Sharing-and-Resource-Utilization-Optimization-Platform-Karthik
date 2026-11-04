import assert from 'node:assert/strict'
import test from 'node:test'
import {
  BOOKING_REFRESH_EVENT,
  getBookingActionError,
  getBookingActionRequest,
  formatBookingStatus,
  getBookingActions,
  performBookingAction,
  getAvailabilityRequest,
  getCheckInAvailability,
} from '../src/access/bookingWorkflow.js'

const pendingOwn = { id: 23, userId: 7, status: 'PENDING' }

test('researcher may cancel only their own pending or approved booking', () => {
  assert.deepEqual(getBookingActions(pendingOwn, ['RESEARCHER_STUDENT'], 7), ['cancel'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'APPROVED' }, ['RESEARCHER_STUDENT'], 7), ['cancel', 'check-in'])
  assert.deepEqual(getBookingActions(pendingOwn, ['RESEARCHER_STUDENT'], 8), [])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'REJECTED' }, ['RESEARCHER_STUDENT'], 7), [])
})

test('status decisions and lifecycle actions are limited to controller-authorized roles', () => {
  for (const role of ['LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN']) {
    assert.deepEqual(getBookingActions(pendingOwn, [role], 7), ['approve', 'reject'], role)
  }
  for (const role of ['LAB_TECHNICIAN', 'DEPARTMENT_HEAD']) {
    assert.deepEqual(getBookingActions(pendingOwn, [role], 7), role === 'DEPARTMENT_HEAD' ? ['cancel'] : [], role)
  }
  assert.deepEqual(getBookingActions(pendingOwn, ['RESEARCHER_STUDENT'], 7), ['cancel'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'APPROVED' }, ['LAB_MANAGER'], 7), ['check-in', 'no-show'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'IN_USE' }, ['LAB_MANAGER'], 7), ['check-out'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'APPROVED' }, ['RESEARCHER_STUDENT'], 7), ['cancel', 'check-in'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'IN_USE' }, ['RESEARCHER_STUDENT'], 7), ['check-out'])
  assert.deepEqual(getBookingActions({ ...pendingOwn, status: 'COMPLETED' }, ['LAB_MANAGER'], 7), [])
  assert.deepEqual(getBookingActions(pendingOwn, [], 7), [])
  assert.deepEqual(getBookingActions(pendingOwn, ['UNKNOWN_ROLE'], 7), [])
})

test('requests use existing booking API routes and backend payloads', () => {
  assert.deepEqual(getBookingActionRequest('cancel', 23), {
    method: 'patch', url: '/api/bookings/23/cancel', data: undefined,
  })
  assert.deepEqual(getBookingActionRequest('approve', 23), {
    method: 'patch', url: '/api/bookings/23/status', data: { status: 'APPROVED' },
  })
  assert.deepEqual(getBookingActionRequest('reject', 23), {
    method: 'patch', url: '/api/bookings/23/status', data: { status: 'REJECTED' },
  })
  assert.deepEqual(getBookingActionRequest('check-in', 23), {
    method: 'post', url: '/api/bookings/23/check-in', data: undefined,
  })
  assert.deepEqual(getBookingActionRequest('check-out', 23), {
    method: 'post', url: '/api/bookings/23/check-out', data: undefined,
  })
})

test('authorized action refreshes bookings and notifies an open dashboard', async () => {
  const calls = []
  await performBookingAction({
    api: { patch: async (...args) => calls.push(args) },
    action: 'approve',
    bookingId: 23,
    refresh: async () => calls.push('refresh-bookings'),
    notifyDashboard: () => calls.push(BOOKING_REFRESH_EVENT),
  })
  assert.deepEqual(calls, [
    ['/api/bookings/23/status', { status: 'APPROVED' }],
    'refresh-bookings',
    BOOKING_REFRESH_EVENT,
  ])
})

test('API failure is surfaced without refreshing or notifying the dashboard', async () => {
  const forbidden = { response: { status: 403, data: { message: 'denied' } } }
  assert.match(getBookingActionError(forbidden, 'fallback'), /Access denied/)
  assert.equal(getBookingActionError({ response: { status: 400, data: { message: 'Only pending bookings can change status' } } }, 'fallback'), 'Only pending bookings can change status')
  const forbiddenMessage = getBookingActionError({ response: { status: 403, data: { message: 'Forbidden' } } }, 'fallback')
  assert.notEqual(forbiddenMessage, 'No bookings found')

  let refreshed = false
  await assert.rejects(performBookingAction({
    api: { patch: async () => { throw forbidden } },
    action: 'cancel',
    bookingId: 23,
    refresh: async () => { refreshed = true },
    notifyDashboard: () => { refreshed = true },
  }), (error) => error === forbidden)
  assert.equal(refreshed, false)
})

test('unsupported actions cannot construct a backend request', () => {
  assert.throws(() => getBookingActionRequest('delete', 23), TypeError)
})

test('persisted booking status names render with the mentor terminology', () => {
  assert.equal(formatBookingStatus('PENDING'), 'Pending Approval')
  assert.equal(formatBookingStatus('APPROVED'), 'Confirmed')
  assert.equal(formatBookingStatus('REJECTED'), 'Rejected')
  assert.equal(formatBookingStatus('CANCELLED'), 'Cancelled')
  assert.equal(formatBookingStatus('IN_USE'), 'In Use')
  assert.equal(formatBookingStatus('COMPLETED'), 'Completed')
  assert.equal(formatBookingStatus('NO_SHOW'), 'No Show')
})

test('availability query uses the existing backend time parameter names', () => {
  assert.deepEqual(getAvailabilityRequest('4', '2026-11-01T09:00:00', '2026-11-01T10:00:00'), {
    url: '/api/bookings/availability',
    params: { equipmentId: '4', startTime: '2026-11-01T09:00:00', endTime: '2026-11-01T10:00:00' },
  })
  assert.throws(() => getAvailabilityRequest(4, '2026-11-01T10:00:00', '2026-11-01T10:00:00'))
})

test('check-in is visible only to its owner from 15 minutes before start through end', () => {
  const booking = { id: 1, userId: 7, status: 'CONFIRMED', startTime: '2026-10-31T10:00:00', endTime: '2026-10-31T11:00:00' }
  const start = new Date('2026-10-31T10:00:00').getTime()
  assert.equal(getCheckInAvailability(booking, 7, start - 15 * 60 * 1000).eligible, true)
  assert.equal(getCheckInAvailability(booking, 7, start - 15 * 60 * 1000 - 1).eligible, false)
  assert.equal(getCheckInAvailability(booking, 8, start).visible, false)
  assert.equal(getCheckInAvailability({ ...booking, status: 'COMPLETED' }, 7, start).visible, false)
  assert.equal(getCheckInAvailability(booking, 7, new Date('2026-10-31T11:00:00').getTime()).eligible, true)
  assert.equal(getCheckInAvailability(booking, 7, new Date('2026-10-31T11:00:00').getTime() + 1).eligible, false)
})
