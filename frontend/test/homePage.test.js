import assert from 'node:assert/strict'
import test from 'node:test'
import {
  getLoginDestination,
  getRoleGuidance,
  getRoleHomeItems,
  resolveHomeView,
  ROLE_LABELS,
  SUPPORTED_ROLES,
} from '../src/access/homeAccess.js'
import {
  clearSession,
  persistSession,
  readSession,
} from '../src/context/authSession.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  }
}

test('all six supported roles produce role-specific home navigation items', () => {
  for (const role of SUPPORTED_ROLES) {
    const items = getRoleHomeItems([role])
    assert.ok(Array.isArray(items), `items for role ${role} should be an array`)
    assert.ok(items.length > 0, `items for role ${role} should not be empty`)

    // Verify all items have required fields
    for (const item of items) {
      assert.ok(item.icon, `item ${item.title} should have an icon`)
      assert.ok(item.title, `item should have a title`)
      assert.ok(item.description, `item ${item.title} should have a description`)
      assert.ok(item.path, `item ${item.title} should have a path`)
      assert.ok(item.action, `item ${item.title} should have an action label`)
    }
  }
})

test('researcher role does not receive administrator approval or onboarding items', () => {
  const items = getRoleHomeItems(['RESEARCHER_STUDENT'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Equipment Catalog'))
  assert.ok(titles.includes('My Bookings'))
  assert.ok(titles.includes('Booking Calendar'))
  assert.ok(titles.includes('Report Maintenance'))
  assert.ok(!titles.includes('Membership Approvals'))
  assert.ok(!titles.includes('Institution Onboarding'))
  assert.ok(!titles.includes('Platform Administrators'))
  assert.ok(!titles.includes('Department Management'))
})

test('institution administrator receives department management and membership approvals', () => {
  const items = getRoleHomeItems(['INSTITUTION_ADMIN'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Membership Approvals'))
  assert.ok(titles.includes('Department Management'))
  assert.ok(titles.includes('Institution Equipment'))
  assert.ok(!titles.includes('Platform Administrators'))
  assert.ok(!titles.includes('Institution Onboarding'))
})

test('system administrator receives platform governance and institution onboarding', () => {
  const items = getRoleHomeItems(['SYSTEM_ADMIN'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Institution Onboarding'))
  assert.ok(titles.includes('Platform Administrators'))
  assert.ok(titles.includes('Platform Directory'))
  assert.ok(titles.includes('Platform Equipment'))
})

test('lab manager receives booking decision items and inventory controls', () => {
  const items = getRoleHomeItems(['LAB_MANAGER'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Booking Approvals'))
  assert.ok(titles.includes('Equipment Inventory'))
})

test('lab technician receives maintenance operations and equipment calibrations', () => {
  const items = getRoleHomeItems(['LAB_TECHNICIAN'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Maintenance Operations'))
  assert.ok(titles.includes('Equipment & Calibrations'))
})

test('department head receives department utilization and departmental equipment', () => {
  const items = getRoleHomeItems(['DEPARTMENT_HEAD'])
  const titles = items.map((i) => i.title)
  assert.ok(titles.includes('Department Utilization'))
  assert.ok(titles.includes('Department Equipment'))
})

test('multiple roles deduplicate and aggregate unique feature modules', () => {
  const dualItems = getRoleHomeItems(['SYSTEM_ADMIN', 'INSTITUTION_ADMIN'])
  const titles = dualItems.map((i) => i.title)
  assert.equal(new Set(titles).size, titles.length, 'titles must be unique')
  assert.ok(titles.includes('Institution Onboarding'))
  assert.ok(titles.includes('Department Management'))
  assert.ok(titles.includes('Membership Approvals'))
})

test('invalid or empty roles return empty items and empty guidance', () => {
  assert.deepEqual(getRoleHomeItems([]), [])
  assert.deepEqual(getRoleHomeItems(null), [])
  assert.deepEqual(getRoleHomeItems(['UNKNOWN_ROLE']), [])
  assert.equal(getRoleGuidance([]), '')
  assert.equal(getRoleGuidance(null), '')
})

test('role guidance gives specific descriptions for each role', () => {
  assert.match(getRoleGuidance(['SYSTEM_ADMIN']), /System Administrator/)
  assert.match(getRoleGuidance(['INSTITUTION_ADMIN']), /Institution Administrator/)
  assert.match(getRoleGuidance(['LAB_MANAGER']), /Lab Manager/)
  assert.match(getRoleGuidance(['DEPARTMENT_HEAD']), /Department Head/)
  assert.match(getRoleGuidance(['LAB_TECHNICIAN']), /Lab Technician/)
  assert.match(getRoleGuidance(['RESEARCHER_STUDENT']), /Researcher or Student/)
})

test('login destination redirects to home / by default or preserves valid deep link', () => {
  assert.equal(getLoginDestination(null), '/')
  assert.equal(getLoginDestination({ pathname: '/login' }), '/')
  assert.equal(getLoginDestination({ pathname: '/register' }), '/')
  assert.equal(getLoginDestination({ pathname: '/password-reset' }), '/')
  assert.equal(getLoginDestination({ pathname: '/equipment' }), '/equipment')
  assert.equal(
    getLoginDestination({ pathname: '/bookings', search: '?status=PENDING' }),
    '/bookings?status=PENDING',
  )
})

test('role labels match the six mentor-approved labels', () => {
  assert.equal(ROLE_LABELS.RESEARCHER_STUDENT, 'Researcher / Student')
  assert.equal(ROLE_LABELS.LAB_TECHNICIAN, 'Lab Technician')
  assert.equal(ROLE_LABELS.LAB_MANAGER, 'Lab Manager')
  assert.equal(ROLE_LABELS.DEPARTMENT_HEAD, 'Department Head')
  assert.equal(ROLE_LABELS.INSTITUTION_ADMIN, 'Institution Administrator')
  assert.equal(ROLE_LABELS.SYSTEM_ADMIN, 'System Administrator')
})

// Scenario 1: Logged-out user opens /: public landing page appears
test('Scenario 1: logged-out user visiting / resolves to public landing page', () => {
  const emptyStorage = memoryStorage()
  const session = readSession(emptyStorage)
  assert.equal(resolveHomeView(session), 'public')
})

// Scenario 2: Logged-out user clicks Sign in and successfully logs in: authenticated Home page appears
test('Scenario 2: user login sets session and redirects to / where authenticated home page resolves', () => {
  const storage = memoryStorage()
  const user = { id: 10, email: 'researcher@example.edu', institutionId: 3, roles: ['RESEARCHER_STUDENT'] }
  persistSession(storage, 'jwt-token-123', user)
  const restoredSession = readSession(storage)
  assert.equal(getLoginDestination(null), '/')
  assert.equal(resolveHomeView(restoredSession), 'authenticated')
})

// Scenario 3: Authenticated user clicks Home: public landing page does not appear
test('Scenario 3: authenticated user clicking Home remains in authenticated view', () => {
  const session = {
    token: 'valid-token',
    user: { id: 10, email: 'user@example.edu', roles: ['LAB_MANAGER'] },
  }
  assert.equal(resolveHomeView(session), 'authenticated')
})

// Scenario 4: Authenticated user refreshes /: authenticated Home page remains visible
test('Scenario 4: browser refresh restores session synchronously and maintains authenticated home', () => {
  const storage = memoryStorage()
  const user = { id: 5, email: 'admin@college.edu', institutionId: 1, roles: ['INSTITUTION_ADMIN'] }
  persistSession(storage, 'valid-token-admin', user)

  // Simulation of browser refresh: readSession runs on page initialization
  const refreshedSession = readSession(storage)
  assert.equal(resolveHomeView(refreshedSession), 'authenticated')
})

// Scenario 5: Authenticated user directly enters http://localhost:5173/: authenticated Home page appears
test('Scenario 5: direct entry to / loads stored session and displays authenticated home', () => {
  const storage = memoryStorage()
  const user = { id: 99, email: 'head@college.edu', institutionId: 1, roles: ['DEPARTMENT_HEAD'] }
  persistSession(storage, 'head-token', user)

  const directEntrySession = readSession(storage)
  assert.equal(resolveHomeView(directEntrySession), 'authenticated')
})

// Scenario 6: User logs out: public landing page appears
test('Scenario 6: user logout clears session and switches home view to public landing page', () => {
  const storage = memoryStorage()
  const user = { id: 1, email: 'tech@lab.edu', institutionId: 2, roles: ['LAB_TECHNICIAN'] }
  persistSession(storage, 'tech-token', user)
  assert.equal(resolveHomeView(readSession(storage)), 'authenticated')

  clearSession(storage)
  const afterLogoutSession = readSession(storage)
  assert.equal(resolveHomeView(afterLogoutSession), 'public')
})

// Scenario 7: Expired/invalid authentication token: user is handled correctly and is not treated as authenticated
test('Scenario 7: invalid or expired tokens discard session and resolve to public landing page', () => {
  const storage = memoryStorage()

  // Malformed JSON user
  storage.setItem('token', 'valid-looking-token')
  storage.setItem('user', '{invalid-json')
  assert.equal(resolveHomeView(readSession(storage)), 'public')

  // Missing token
  storage.setItem('user', JSON.stringify({ id: 1, email: 'a@b.com', roles: ['RESEARCHER_STUDENT'] }))
  storage.removeItem('token')
  assert.equal(resolveHomeView(readSession(storage)), 'public')

  // Untrusted or missing roles
  storage.setItem('token', 'token')
  storage.setItem('user', JSON.stringify({ id: 1, email: 'a@b.com', roles: ['SUPERUSER'] }))
  assert.equal(resolveHomeView(readSession(storage)), 'public')
})

// Scenario 8: Verify the behaviour for all six roles without bypassing role restrictions
test('Scenario 8: all six roles receive authenticated home view with strict role scoping', () => {
  for (const role of SUPPORTED_ROLES) {
    const session = {
      token: 'valid-role-token',
      user: { id: 7, email: `${role.toLowerCase()}@platform.edu`, institutionId: 1, roles: [role] },
    }
    assert.equal(resolveHomeView(session), 'authenticated', `Role ${role} must resolve to authenticated view`)

    const items = getRoleHomeItems([role])
    assert.ok(items.length > 0, `Role ${role} must have home items`)

    // Verify non-admins never get admin-only modules
    if (role === 'RESEARCHER_STUDENT' || role === 'LAB_TECHNICIAN') {
      const titles = items.map((i) => i.title)
      assert.ok(!titles.includes('Membership Approvals'), `${role} must not see Membership Approvals`)
      assert.ok(!titles.includes('Institution Onboarding'), `${role} must not see Institution Onboarding`)
      assert.ok(!titles.includes('Platform Administrators'), `${role} must not see Platform Administrators`)
    }
  }
})
