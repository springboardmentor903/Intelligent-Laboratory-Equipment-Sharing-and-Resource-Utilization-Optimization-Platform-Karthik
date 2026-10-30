import assert from 'node:assert/strict'
import test from 'node:test'
import { getProfileDetails } from '../src/access/profile.js'
import { toBookingCalendarEvents } from '../src/access/bookingCalendar.js'

test('profile uses trusted session fields and resolves the six role labels', () => {
  assert.deepEqual(getProfileDetails({
    id: 17,
    email: 'researcher@example.edu',
    institutionId: 4,
    roles: ['RESEARCHER_STUDENT', 'LAB_TECHNICIAN'],
  }), {
    email: 'researcher@example.edu',
    userId: 17,
    institutionId: 4,
    roles: ['Researcher / Student', 'Lab Technician'],
  })
  assert.equal(getProfileDetails(null), null)
})

test('calendar events preserve LocalDateTime wall-clock strings and skip incomplete rows', () => {
  assert.deepEqual(toBookingCalendarEvents([
    { id: 3, equipmentId: 8, status: 'PENDING', startTime: '2030-04-03T09:15:00', endTime: '2030-04-03T10:30:00', purpose: 'Microscopy' },
    { id: 4, equipmentId: 9, status: 'APPROVED', startTime: '2030-04-04T09:00:00', endTime: null },
  ]), [{
    id: '3',
    title: 'Equipment #8 · Pending Approval',
    start: '2030-04-03T09:15:00',
    end: '2030-04-03T10:30:00',
    extendedProps: { bookingId: 3, equipmentId: 8, status: 'PENDING', purpose: 'Microscopy' },
  }])
  assert.deepEqual(toBookingCalendarEvents(null), [])
})
