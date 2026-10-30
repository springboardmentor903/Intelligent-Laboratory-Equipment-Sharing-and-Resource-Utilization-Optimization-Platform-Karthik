import assert from 'node:assert/strict'
import test from 'node:test'
import { getResetTokenFromHash } from '../src/access/passwordReset.js'

test('password reset page can consume a private token from the URL fragment', () => {
  assert.equal(getResetTokenFromHash('#token=opaque-reset-code'), 'opaque-reset-code')
  assert.equal(getResetTokenFromHash(''), '')
  assert.equal(getResetTokenFromHash('#unrelated=value'), '')
})
