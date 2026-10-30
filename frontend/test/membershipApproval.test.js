import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canManageMemberships,
  membershipApprovalError,
  PENDING_MEMBERSHIPS_URL,
} from '../src/access/membershipApproval.js'

test('membership management is visible only to an institution administrator role', () => {
  assert.equal(canManageMemberships(['INSTITUTION_ADMIN']), true)
  assert.equal(canManageMemberships(['SYSTEM_ADMIN']), false)
  assert.equal(canManageMemberships(['LAB_MANAGER']), false)
  assert.equal(canManageMemberships([]), false)
  assert.equal(PENDING_MEMBERSHIPS_URL, '/api/users/pending-membership')
})

test('membership errors distinguish expired sessions and forbidden access', () => {
  assert.match(membershipApprovalError({ response: { status: 401 } }), /session has expired/i)
  assert.match(membershipApprovalError({ response: { status: 403 } }), /institution administrator/i)
})
