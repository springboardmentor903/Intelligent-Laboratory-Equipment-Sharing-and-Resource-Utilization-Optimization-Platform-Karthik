import assert from 'node:assert/strict'
import test from 'node:test'
import {
  getUtilizationPermissions,
  utilizationApiError,
  validateDateRange,
  validateUtilizationCreate,
  validateUtilizationUpdate,
} from '../src/access/utilizationWorkflow.js'

test('utilization CRUD visibility follows the controller role rules', () => {
  assert.deepEqual(getUtilizationPermissions(['RESEARCHER_STUDENT']), {
    canCreate: true, canEdit: false, canDelete: false,
  })
  assert.deepEqual(getUtilizationPermissions(['DEPARTMENT_HEAD']), {
    canCreate: false, canEdit: false, canDelete: false,
  })
  for (const role of ['LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN']) {
    assert.deepEqual(getUtilizationPermissions([role]), {
      canCreate: true, canEdit: true, canDelete: true,
    }, role)
  }
  assert.deepEqual(getUtilizationPermissions(['UNKNOWN_ROLE']), {
    canCreate: false, canEdit: false, canDelete: false,
  })
})

test('create payload has only writable backend fields and omits frontend user identity', () => {
  const valid = validateUtilizationCreate({
    equipmentId: '17', bookingId: '32', startTime: '2026-07-15T09:00',
    endTime: '2026-07-15T10:30', notes: '  microscope use  ', userId: '999',
  })
  assert.deepEqual(valid.errors, [])
  assert.deepEqual(valid.body, {
    equipmentId: 17, bookingId: 32, startTime: '2026-07-15T09:00',
    endTime: '2026-07-15T10:30', notes: 'microscope use',
  })
  assert.equal(Object.hasOwn(valid.body, 'userId'), false)
  assert.equal(Object.hasOwn(valid.body, 'actualHours'), false)
})

test('create validation rejects invalid IDs, timestamps, order, and oversized notes', () => {
  const result = validateUtilizationCreate({
    equipmentId: '0', bookingId: '1.2', startTime: '2026-02-30T09:00',
    endTime: '2026-02-28T09:00', notes: 'x'.repeat(501),
  })
  assert.ok(result.errors.some((message) => message.includes('equipment')))
  assert.ok(result.errors.some((message) => message.includes('Booking ID')))
  assert.ok(result.errors.some((message) => message.includes('valid start time')))
  assert.ok(result.errors.some((message) => message.includes('500 characters')))
})

test('update payload only contains service-supported mutable fields', () => {
  const result = validateUtilizationUpdate({
    endTime: '2026-07-15T12:00', notes: '', userId: 80, equipmentId: 999,
  }, { startTime: '2026-07-15T09:00:00' })
  assert.deepEqual(result.errors, [])
  assert.deepEqual(result.body, { endTime: '2026-07-15T12:00', notes: '' })
  assert.deepEqual(validateUtilizationUpdate({ endTime: '2026-07-15T09:00', notes: '' }, {
    startTime: '2026-07-15T09:00:30',
  }).errors, ['End time must be after the recorded start time.'])
})

test('date range uses exact query parameter names and local time boundaries', () => {
  assert.deepEqual(validateDateRange('2026-07-01', '2026-07-31'), {
    errors: [],
    params: { start: '2026-07-01T00:00:00', end: '2026-07-31T23:59:59' },
  })
  assert.deepEqual(validateDateRange('2026-08-01', '2026-07-31').errors, [
    'The start date must not be after the end date.',
  ])
  assert.deepEqual(validateDateRange('2026-02-30', '2026-03-01').errors, [
    'Select valid start and end dates.',
  ])
})

test('unauthenticated and forbidden API errors are distinct', () => {
  assert.match(utilizationApiError({ response: { status: 401 } }, ''), /session has expired/i)
  assert.match(utilizationApiError({ response: { status: 403 } }, ''), /access denied/i)
  assert.equal(utilizationApiError({ response: { status: 400, data: { message: 'End time must be after start time' } } }, ''), 'End time must be after start time')
})
