import assert from 'node:assert/strict'
import test from 'node:test'
import {
  bookingsUrlForUser,
  clearSession,
  hasValidRoles,
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

test('session is persisted, restored, and fully cleared on logout', () => {
  const storage = memoryStorage()
  const user = {
    id: 42,
    email: 'researcher@example.invalid',
    institutionId: 7,
    roles: ['RESEARCHER_STUDENT'],
  }

  persistSession(storage, 'opaque-test-token', user)
  assert.deepEqual(readSession(storage), { token: 'opaque-test-token', user })

  clearSession(storage)
  assert.deepEqual(readSession(storage), { token: null, user: null })
})

test('incomplete or malformed stored sessions are discarded', () => {
  const storage = memoryStorage()
  storage.setItem('token', 'opaque-test-token')
  storage.setItem('user', '{invalid')

  assert.deepEqual(readSession(storage), { token: null, user: null })
  assert.equal(storage.getItem('token'), null)
  assert.equal(storage.getItem('user'), null)
})

test('legacy sessions without a trusted role require reauthentication', () => {
  const storage = memoryStorage()
  storage.setItem('token', 'opaque-test-token')
  storage.setItem('user', JSON.stringify({
    id: 42,
    email: 'researcher@example.invalid',
    institutionId: 7,
  }))

  assert.deepEqual(readSession(storage), { token: null, user: null })
  assert.equal(storage.getItem('token'), null)
  assert.equal(storage.getItem('user'), null)
})

test('role data must contain known backend role names', () => {
  assert.equal(hasValidRoles(['RESEARCHER_STUDENT', 'LAB_MANAGER']), true)
  assert.equal(hasValidRoles([]), false)
  assert.equal(hasValidRoles(['UNTRUSTED_ROLE']), false)
})

test('booking history URL is scoped to the signed-in user ID', () => {
  assert.equal(bookingsUrlForUser(42), '/api/bookings/user/42')
  assert.throws(() => bookingsUrlForUser(null), /user ID is required/)
})
