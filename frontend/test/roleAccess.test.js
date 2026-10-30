import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canRequestBooking,
  BOOKING_LIST_ROLES,
  getDashboardAccessPlan,
  getMaintenanceAccessPlan,
  getNavigationItems,
  hasValidRoles,
  SUPPORTED_ROLES,
} from '../src/access/roleAccess.js'

const commonNavigation = [
  '/dashboard',
  '/equipment',
  '/bookings',
  '/booking-calendar',
  '/utilization',
  '/maintenance',
  '/profile',
]

test('all six supported roles receive only the routes their backend view permits', () => {
  assert.deepEqual(SUPPORTED_ROLES, [
    'RESEARCHER_STUDENT',
    'LAB_TECHNICIAN',
    'LAB_MANAGER',
    'DEPARTMENT_HEAD',
    'INSTITUTION_ADMIN',
    'SYSTEM_ADMIN',
  ])

  for (const role of SUPPORTED_ROLES) {
    assert.deepEqual(
      getNavigationItems([role]).map(({ to }) => to),
      commonNavigation,
      `${role} navigation`,
    )
  }
})

test('missing or invalid roles do not create an authenticated navigation plan', () => {
  assert.equal(hasValidRoles(undefined), false)
  assert.equal(hasValidRoles([]), false)
  assert.equal(hasValidRoles(['UNTRUSTED_ROLE']), false)
  assert.deepEqual(getNavigationItems(undefined), [])
  assert.equal(getDashboardAccessPlan(undefined, 12), null)
  assert.equal(getMaintenanceAccessPlan(['UNTRUSTED_ROLE'], 12).canView, false)
})

test('maintenance API requests are owner scoped for researchers and staff scoped for staff roles', () => {
  assert.deepEqual(getMaintenanceAccessPlan(['RESEARCHER_STUDENT'], 12), {
    canView: true,
    recordsUrl: '/api/maintenance/reporter/12',
    summaryUrl: null,
    scope: 'owner',
  })

  for (const role of BOOKING_LIST_ROLES) {
    assert.deepEqual(getMaintenanceAccessPlan([role], 12), {
      canView: true,
      recordsUrl: '/api/maintenance',
      summaryUrl: '/api/maintenance/summary',
      scope: role === 'SYSTEM_ADMIN' ? 'platform' : 'institution',
    }, `${role} maintenance access`)
  }

  assert.equal(getMaintenanceAccessPlan(['RESEARCHER_STUDENT'], null).canView, false)
})

test('dashboard uses own bookings and suppresses researcher maintenance summary', () => {
  const researcher = getDashboardAccessPlan(['RESEARCHER_STUDENT'], 12)
  assert.equal(researcher.bookingsUrl, '/api/bookings/user/12')
  assert.equal(researcher.maintenanceSummaryUrl, null)
  assert.equal(researcher.bookingScope, 'owner')
  assert.equal(canRequestBooking(['RESEARCHER_STUDENT']), true)

  for (const role of BOOKING_LIST_ROLES) {
    const staff = getDashboardAccessPlan([role], 12)
    assert.equal(staff.bookingsUrl, '/api/bookings', `${role} booking list`)
    assert.equal(staff.maintenanceSummaryUrl, '/api/maintenance/summary', `${role} summary`)
    assert.equal(
      staff.bookingScope,
      role === 'SYSTEM_ADMIN' ? 'platform' : 'institution',
      `${role} booking scope`,
    )
    assert.equal(canRequestBooking([role]), false)
  }
})
