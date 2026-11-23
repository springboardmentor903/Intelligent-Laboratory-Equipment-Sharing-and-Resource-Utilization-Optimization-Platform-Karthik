import test from 'node:test'
import assert from 'node:assert/strict'
import {
  validateRejectionRemarks,
  validateAllowedHours,
  buildApprovePayload,
  buildRejectPayload,
  isIncomingRequestReviewable,
  getSharingAgreementSummary,
  createReviewDrawerState,
  switchReviewMode,
  closeReviewDrawerState,
} from '../src/access/sharingReviewWorkflow.js'

test('rejection remarks validation requires non-empty content and enforces length bound', () => {
  assert.equal(validateRejectionRemarks(''), 'Rejection remarks are required.')
  assert.equal(validateRejectionRemarks('   '), 'Rejection remarks are required.')
  assert.equal(validateRejectionRemarks(null), 'Rejection remarks are required.')
  assert.equal(validateRejectionRemarks('Valid reason for rejection'), '')
  assert.equal(validateRejectionRemarks('a'.repeat(500)), '')
  assert.equal(validateRejectionRemarks('a'.repeat(501)), 'Rejection remarks cannot exceed 500 characters.')
})

test('allowed booking hours validation enforces matching boundaries and sequential ordering', () => {
  assert.equal(validateAllowedHours('', ''), '')
  assert.equal(validateAllowedHours('09:00', ''), 'Both allowed start and end times must be specified together.')
  assert.equal(validateAllowedHours('', '17:00'), 'Both allowed start and end times must be specified together.')
  assert.equal(validateAllowedHours('17:00', '09:00'), 'Allowed end time must be after allowed start time.')
  assert.equal(validateAllowedHours('12:00', '12:00'), 'Allowed end time must be after allowed start time.')
  assert.equal(validateAllowedHours('09:00', '17:00'), '')
})

test('buildApprovePayload formats decision payload with optional remarks and hours', () => {
  const minimal = buildApprovePayload()
  assert.deepEqual(minimal, { action: 'APPROVE' })

  const withRemarks = buildApprovePayload({ remarks: '  Standard partner agreement  ' })
  assert.deepEqual(withRemarks, {
    action: 'APPROVE',
    remarks: 'Standard partner agreement',
  })

  const withHours = buildApprovePayload({
    remarks: 'Approved',
    allowedStartTime: '08:30',
    allowedEndTime: '18:00',
  })
  assert.deepEqual(withHours, {
    action: 'APPROVE',
    remarks: 'Approved',
    allowedStartTime: '08:30',
    allowedEndTime: '18:00',
  })
})

test('buildRejectPayload trims and packages mandatory rejection remarks', () => {
  const payload = buildRejectPayload({ remarks: '  Equipment unavailable for external researchers  ' })
  assert.deepEqual(payload, {
    action: 'REJECT',
    remarks: 'Equipment unavailable for external researchers',
  })
})

test('isIncomingRequestReviewable enforces FORWARDED status and administrator roles', () => {
  const forwardedReq = { id: 5, status: 'FORWARDED' }
  const pendingReq = { id: 6, status: 'PENDING' }
  const approvedReq = { id: 7, status: 'APPROVED' }

  assert.equal(isIncomingRequestReviewable(forwardedReq, ['INSTITUTION_ADMIN']), true)
  assert.equal(isIncomingRequestReviewable(forwardedReq, ['SYSTEM_ADMIN']), true)
  assert.equal(isIncomingRequestReviewable(forwardedReq, ['RESEARCHER_STUDENT']), false)
  assert.equal(isIncomingRequestReviewable(forwardedReq, ['LAB_MANAGER']), false)

  assert.equal(isIncomingRequestReviewable(pendingReq, ['INSTITUTION_ADMIN']), false)
  assert.equal(isIncomingRequestReviewable(approvedReq, ['INSTITUTION_ADMIN']), false)
  assert.equal(isIncomingRequestReviewable(null, ['INSTITUTION_ADMIN']), false)
})

test('getSharingAgreementSummary snapshots rate, recipient, dates, and stage-1 audit info', () => {
  const request = {
    id: 12,
    equipmentId: 44,
    equipmentName: 'High-Res Electron Microscope',
    assetTag: 'EM-009',
    requesterInstitutionId: 3,
    partnerInstitution: 'Apex University',
    requesterId: 101,
    requesterName: 'Dr. Jane Smith',
    requesterEmail: 'jane@apex.edu',
    startDate: '2026-12-01T09:00:00',
    endDate: '2026-12-15T18:00:00',
    feePerHour: '1800.00',
    requesterReviewedBy: 7,
    requesterReviewedAt: '2026-11-20T14:30:00',
    requesterReviewRemarks: 'Approved for research collaboration',
  }

  const summary = getSharingAgreementSummary(request, { feePerHour: '2000.00' })
  assert.equal(summary.equipmentName, 'High-Res Electron Microscope')
  assert.equal(summary.assetTag, 'EM-009')
  assert.equal(summary.partnerInstitution, 'Apex University')
  assert.equal(summary.recipient, 'Dr. Jane Smith')
  assert.equal(summary.startDate, '2026-12-01T09:00:00')
  assert.equal(summary.endDate, '2026-12-15T18:00:00')
  assert.equal(summary.feePerHour, '2000.00') // Takes listing rate snapshot first
  assert.equal(summary.firstStageReviewer, 'Institution Admin #7')
  assert.equal(summary.firstStageRemarks, 'Approved for research collaboration')
})

test('getSharingAgreementSummary falls back cleanly on snake_case requester_review_remarks', () => {
  const request = {
    id: 14,
    equipmentId: 10,
    requesterInstitutionId: 2,
    requesterId: 99,
    startDate: '2026-12-05T10:00:00',
    endDate: '2026-12-06T18:00:00',
    requester_review_remarks: 'Home admin approved under MoU terms',
  }
  const summary = getSharingAgreementSummary(request)
  assert.equal(summary.firstStageRemarks, 'Home admin approved under MoU terms')
  assert.equal(summary.recipient, 'User #99')
})

test('createReviewDrawerState initializes slide-over panel with active request and mode', () => {
  const req = { id: 25, status: 'FORWARDED' }
  const approveState = createReviewDrawerState(req, 'APPROVE')
  assert.equal(approveState.isOpen, true)
  assert.equal(approveState.request.id, 25)
  assert.equal(approveState.mode, 'APPROVE')
  assert.equal(approveState.error, '')
  assert.equal(approveState.rejectRemarks, '')

  const rejectState = createReviewDrawerState(req, 'REJECT')
  assert.equal(rejectState.isOpen, true)
  assert.equal(rejectState.mode, 'REJECT')

  const closedState = createReviewDrawerState(null)
  assert.equal(closedState.isOpen, false)
  assert.equal(closedState.request, null)
})

test('switchReviewMode switches decision tabs in slide-over drawer and resets inline error', () => {
  const initial = {
    ...createReviewDrawerState({ id: 30 }),
    error: 'Previous validation error',
  }
  const switched = switchReviewMode(initial, 'REJECT')
  assert.equal(switched.mode, 'REJECT')
  assert.equal(switched.error, '')

  const switchedBack = switchReviewMode(switched, 'APPROVE')
  assert.equal(switchedBack.mode, 'APPROVE')
})

test('closeReviewDrawerState resets slide-over review drawer state completely', () => {
  const state = closeReviewDrawerState()
  assert.equal(state.isOpen, false)
  assert.equal(state.request, null)
  assert.equal(state.approvalRemarks, '')
  assert.equal(state.rejectRemarks, '')
})

