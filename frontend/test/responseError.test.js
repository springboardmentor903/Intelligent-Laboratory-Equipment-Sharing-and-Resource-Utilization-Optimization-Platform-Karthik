import assert from 'node:assert/strict'
import test from 'node:test'
import { handleApiError } from '../src/api/responseError.js'

function memoryStorage() {
  const values = new Map([['token', 'opaque-test-token'], ['user', '{"id":42}']])
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  }
}

function mockWindow(pathname = '/dashboard') {
  const events = []
  const redirects = []
  return {
    events,
    redirects,
    location: { pathname, replace: (path) => redirects.push(path) },
    dispatchEvent: (event) => events.push(event.type),
  }
}

test('401 clears session and redirects once unless already on login', () => {
  const storage = memoryStorage()
  const windowObject = mockWindow('/bookings')
  const error = { response: { status: 401, data: {} } }

  assert.equal(handleApiError(error, { storage, windowObject }), error)
  assert.equal(storage.getItem('token'), null)
  assert.equal(storage.getItem('user'), null)
  assert.deepEqual(windowObject.redirects, ['/login'])
  assert.deepEqual(windowObject.events, ['lab-auth-unauthorized'])

  const loginWindow = mockWindow('/login')
  handleApiError({ response: { status: 401, data: {} } }, {
    storage: memoryStorage(),
    windowObject: loginWindow,
  })
  assert.deepEqual(loginWindow.redirects, [])
})

test('403 provides one access-denied message and preserves the session', () => {
  const storage = memoryStorage()
  const windowObject = mockWindow('/dashboard')
  const error = { response: { status: 403, data: { error: 'Forbidden' } } }

  handleApiError(error, { storage, windowObject })
  assert.equal(error.response.data.message, 'Access denied. Your account is not permitted to perform this action.')
  assert.equal(storage.getItem('token'), 'opaque-test-token')
  assert.deepEqual(windowObject.redirects, [])
  assert.deepEqual(windowObject.events, [])
})

test('pending membership 403 keeps its specific login message without clearing a session', () => {
  const storage = memoryStorage()
  const windowObject = mockWindow('/login')
  const error = { response: { status: 403, data: {
    code: 'MEMBERSHIP_PENDING',
    message: 'Your institution membership is awaiting administrator approval.',
  } } }

  handleApiError(error, { storage, windowObject })

  assert.equal(error.response.data.message, 'Your institution membership is awaiting administrator approval.')
  assert.equal(storage.getItem('token'), 'opaque-test-token')
  assert.deepEqual(windowObject.redirects, [])
})

test('rejected membership 403 keeps its specific login message without clearing a session', () => {
  const storage = memoryStorage()
  const windowObject = mockWindow('/login')
  const error = { response: { status: 403, data: {
    code: 'MEMBERSHIP_REJECTED',
    message: 'Your institution membership request was rejected by the administrator.',
  } } }

  handleApiError(error, { storage, windowObject })

  assert.equal(error.response.data.message, 'Your institution membership request was rejected by the administrator.')
  assert.equal(storage.getItem('token'), 'opaque-test-token')
  assert.deepEqual(windowObject.redirects, [])
})
