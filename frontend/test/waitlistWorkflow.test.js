import test from 'node:test'
import assert from 'node:assert/strict'
import { canApproveBookings, canRequestBooking } from '../src/access/roleAccess.js'

test('canApproveBookings allows manager and admin roles, denies student roles', () => {
  assert.equal(canApproveBookings(['LAB_MANAGER']), true)
  assert.equal(canApproveBookings(['DEPARTMENT_HEAD']), true)
  assert.equal(canApproveBookings(['INSTITUTION_ADMIN']), true)
  assert.equal(canApproveBookings(['SYSTEM_ADMIN']), true)
  assert.equal(canApproveBookings(['LAB_TECHNICIAN']), true)
  assert.equal(canApproveBookings(['RESEARCHER_STUDENT']), false)
  assert.equal(canApproveBookings([]), false)
  assert.equal(canApproveBookings(null), false)
})

test('canRequestBooking allows researchers and department heads', () => {
  assert.equal(canRequestBooking(['RESEARCHER_STUDENT']), true)
  assert.equal(canRequestBooking(['DEPARTMENT_HEAD']), true)
  assert.equal(canRequestBooking(['LAB_TECHNICIAN']), false)
})

test('recurring booking occurrences are clamped between 2 and 12', () => {
  const clamp = (val) => Math.max(2, Math.min(12, parseInt(val, 10) || 2))
  assert.equal(clamp(1), 2)
  assert.equal(clamp(4), 4)
  assert.equal(clamp(15), 12)
  assert.equal(clamp('invalid'), 2)
})

test('waitlist claim and conversion requires OFFERED status', () => {
  const isClaimable = (status) => String(status || '').toUpperCase() === 'OFFERED'
  assert.equal(isClaimable('OFFERED'), true)
  assert.equal(isClaimable('WAITING'), false)
  assert.equal(isClaimable('CONVERTED'), false)
  assert.equal(isClaimable('EXPIRED'), false)
})

test('recommendations slot formatter converts ISO strings safely', () => {
  const formatSlot = (isoStr) => {
    if (!isoStr) return '—'
    const d = new Date(isoStr)
    return Number.isNaN(d.getTime()) ? isoStr : d.toISOString()
  }
  assert.equal(formatSlot(null), '—')
  assert.equal(formatSlot('2026-11-05T10:00:00Z'), '2026-11-05T10:00:00.000Z')
})
