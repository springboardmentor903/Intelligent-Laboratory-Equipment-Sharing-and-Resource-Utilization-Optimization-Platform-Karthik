import assert from 'node:assert/strict'
import test from 'node:test'
import { getOAuthCallbackResult, googleAuthorizationUrl, oauthFailureMessage } from '../src/access/oauthLogin.js'

test('callback reads the one-time code and generic OAuth errors from the fragment', () => {
  assert.deepEqual(getOAuthCallbackResult('#code=one-time-code'), {
    code: 'one-time-code', error: '',
  })
  assert.equal(getOAuthCallbackResult('#oauth_error=google_login_failed').error, 'google_login_failed')
  assert.match(oauthFailureMessage('google_login_failed'), /approved platform account/)
})

test('authorization link points to the configured backend without trailing slashes', () => {
  assert.equal(googleAuthorizationUrl('https://api.example.test/'), 'https://api.example.test/oauth2/authorization/google')
})
