/**
 * Workflow and validation helpers for inter-institution sharing request reviews.
 */

export function validateRejectionRemarks(remarks) {
  const trimmed = String(remarks || '').trim()
  if (!trimmed) {
    return 'Rejection remarks are required.'
  }
  if (trimmed.length > 500) {
    return 'Rejection remarks cannot exceed 500 characters.'
  }
  return ''
}

export function validateAllowedHours(allowedStartTime, allowedEndTime) {
  const start = String(allowedStartTime || '').trim()
  const end = String(allowedEndTime || '').trim()

  if ((start && !end) || (!start && end)) {
    return 'Both allowed start and end times must be specified together.'
  }
  if (start && end && start >= end) {
    return 'Allowed end time must be after allowed start time.'
  }
  return ''
}

export function buildApprovePayload({ remarks = '', allowedStartTime = '', allowedEndTime = '' } = {}) {
  const payload = {
    action: 'APPROVE',
  }
  const cleanRemarks = String(remarks || '').trim()
  if (cleanRemarks) {
    payload.remarks = cleanRemarks
  }
  const cleanStart = String(allowedStartTime || '').trim()
  const cleanEnd = String(allowedEndTime || '').trim()
  if (cleanStart && cleanEnd) {
    payload.allowedStartTime = cleanStart
    payload.allowedEndTime = cleanEnd
  }
  return payload
}

export function buildRejectPayload({ remarks = '' } = {}) {
  const cleanRemarks = String(remarks || '').trim()
  return {
    action: 'REJECT',
    remarks: cleanRemarks,
  }
}

export function isIncomingRequestReviewable(request, userRoles = []) {
  if (!request) return false
  const status = String(request.status || '').toUpperCase()
  if (status !== 'FORWARDED') return false

  const roles = Array.isArray(userRoles) ? userRoles : []
  return roles.some((r) => ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'].includes(r))
}

export function getSharingAgreementSummary(request, listing = null) {
  if (!request) return null

  const feeSnapshot = listing?.feePerHour != null
    ? listing.feePerHour
    : request?.feePerHour != null
    ? request.feePerHour
    : null

  const recipient =
    request.requesterName ||
    request.requesterEmail ||
    (request.requesterId ? `User #${request.requesterId}` : 'Partner Requester')

  return {
    equipmentName: request.equipmentName || listing?.equipmentName || (request.equipmentId ? `Equipment #${request.equipmentId}` : '—'),
    assetTag: request.assetTag || listing?.assetTag || '—',
    partnerInstitution: request.partnerInstitution || (request.requesterInstitutionId ? `Institution #${request.requesterInstitutionId}` : 'Partner Institution'),
    recipient,
    startDate: request.startDate || request.startTime || null,
    endDate: request.endDate || request.endTime || null,
    feePerHour: feeSnapshot,
    firstStageReviewer: request.requesterReviewedBy ? `Institution Admin #${request.requesterReviewedBy}` : 'Home Institution Admin',
    firstStageReviewedAt: request.requesterReviewedAt || null,
    firstStageRemarks: request.requesterReviewRemarks || request.requester_review_remarks || 'Approved by home institution',
  }
}

export function createReviewDrawerState(request = null, mode = 'APPROVE') {
  return {
    isOpen: Boolean(request),
    request: request || null,
    mode: mode === 'REJECT' ? 'REJECT' : 'APPROVE',
    error: '',
    approvalRemarks: '',
    allowedStartTime: '',
    allowedEndTime: '',
    rejectRemarks: '',
  }
}

export function switchReviewMode(currentState, newMode) {
  return {
    ...currentState,
    mode: newMode === 'REJECT' ? 'REJECT' : 'APPROVE',
    error: '',
  }
}

export function closeReviewDrawerState() {
  return createReviewDrawerState(null, 'APPROVE')
}
