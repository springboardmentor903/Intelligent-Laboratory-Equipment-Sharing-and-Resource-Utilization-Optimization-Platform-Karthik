import assert from 'node:assert/strict'
import test from 'node:test'
import { mapLoginError, validateLoginForm } from '../src/access/loginValidation.js'

test('login validation catches empty email', () => {
  assert.equal(
    validateLoginForm({ email: '', password: 'secretpassword' }),
    'Please enter your email address.',
  )
  assert.equal(
    validateLoginForm({ email: '   ', password: 'secretpassword' }),
    'Please enter your email address.',
  )
  assert.equal(
    validateLoginForm({ email: null, password: 'secretpassword' }),
    'Please enter your email address.',
  )
})

test('login validation catches invalid email format', () => {
  assert.equal(
    validateLoginForm({ email: 'not-an-email', password: 'secretpassword' }),
    'Please enter a valid email address.',
  )
  assert.equal(
    validateLoginForm({ email: 'user@', password: 'secretpassword' }),
    'Please enter a valid email address.',
  )
  assert.equal(
    validateLoginForm({ email: 'user@domain', password: 'secretpassword' }),
    'Please enter a valid email address.',
  )
})

test('login validation catches empty password', () => {
  assert.equal(
    validateLoginForm({ email: 'user@example.com', password: '' }),
    'Please enter your password.',
  )
  assert.equal(
    validateLoginForm({ email: 'user@example.com', password: null }),
    'Please enter your password.',
  )
})

test('login validation succeeds for well-formed email and password', () => {
  assert.equal(
    validateLoginForm({ email: 'user@example.com', password: 'valid-password' }),
    null,
  )
})

test('login error maps 400 and 401 to friendly invalid email or password message', () => {
  assert.equal(
    mapLoginError({ response: { status: 400, data: { error: 'Invalid email or password.' } } }),
    'Invalid email or password. Please try again.',
  )
  assert.equal(
    mapLoginError({ response: { status: 401, data: { message: 'Unauthorized' } } }),
    'Invalid email or password. Please try again.',
  )
})

test('login error maps missing response or network failure to unable to connect', () => {
  assert.equal(
    mapLoginError(new Error('Network Error')),
    'Unable to connect. Please try again later.',
  )
  assert.equal(
    mapLoginError({ message: 'Request failed with status code 500' }),
    'Unable to connect. Please try again later.',
  )
})

test('login error maps 500 server error to unable to connect', () => {
  assert.equal(
    mapLoginError({ response: { status: 500, data: { error: 'Internal Server Error' } } }),
    'Unable to connect. Please try again later.',
  )
})

test('login error preserves pending and rejected membership notices', () => {
  assert.equal(
    mapLoginError({
      response: {
        status: 403,
        data: {
          code: 'MEMBERSHIP_PENDING',
          message: 'Your institution membership is awaiting administrator approval.',
        },
      },
    }),
    'Your institution membership is awaiting administrator approval.',
  )

  assert.equal(
    mapLoginError({
      response: {
        status: 403,
        data: {
          code: 'MEMBERSHIP_REJECTED',
          message: 'Your institution membership request was rejected by the administrator.',
        },
      },
    }),
    'Your institution membership request was rejected by the administrator.',
  )
})
