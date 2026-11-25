import test from 'node:test'
import assert from 'node:assert/strict'
import {
  validateDocumentUpload,
  getCalibrationStateBadge,
  getTechnicianTaskGroupBadge,
  canViewTechnicianWorkload,
  canAccessMyTasks,
} from '../src/access/maintenanceWorkflow.js'

test('validateDocumentUpload enforces file presence, size limits, and valid extensions', () => {
  // Empty check
  assert.equal(validateDocumentUpload(null), 'Select a file to upload.')
  assert.equal(validateDocumentUpload(undefined), 'Select a file to upload.')

  // Size limit check (> 10MB)
  const oversizedFile = { name: 'cert.pdf', size: 11 * 1024 * 1024, type: 'application/pdf' }
  assert.match(validateDocumentUpload(oversizedFile, 10), /exceeds maximum limit/i)

  // Extension / MIME validation
  const invalidExt = { name: 'executable.exe', size: 1024, type: 'application/octet-stream' }
  assert.match(validateDocumentUpload(invalidExt), /Allowed file types are PDF, PNG, and JPEG/i)

  // Valid documents
  const validPdf = { name: 'certificate.pdf', size: 2 * 1024 * 1024, type: 'application/pdf' }
  assert.equal(validateDocumentUpload(validPdf), '')

  const validPng = { name: 'report.png', size: 500 * 1024, type: 'image/png' }
  assert.equal(validateDocumentUpload(validPng), '')

  const validJpg = { name: 'photo.jpeg', size: 800 * 1024, type: 'image/jpeg' }
  assert.equal(validateDocumentUpload(validJpg), '')
})

test('getCalibrationStateBadge maps dynamic states accurately', () => {
  assert.deepEqual(getCalibrationStateBadge('VALID'), { label: 'Valid', tone: 'success' })
  assert.deepEqual(getCalibrationStateBadge('DUE_SOON'), { label: 'Due Soon', tone: 'warning' })
  assert.deepEqual(getCalibrationStateBadge('EXPIRED'), { label: 'Expired', tone: 'danger' })
  assert.deepEqual(getCalibrationStateBadge('NONE'), { label: 'No Record', tone: 'neutral' })
  assert.deepEqual(getCalibrationStateBadge(null), { label: 'No Record', tone: 'neutral' })
  assert.deepEqual(getCalibrationStateBadge('unknown'), { label: 'No Record', tone: 'neutral' })
})

test('getTechnicianTaskGroupBadge maps task priority groups', () => {
  assert.deepEqual(getTechnicianTaskGroupBadge('OVERDUE'), { label: 'Overdue', tone: 'danger' })
  assert.deepEqual(getTechnicianTaskGroupBadge('TODAY'), { label: 'Today', tone: 'info' })
  assert.deepEqual(getTechnicianTaskGroupBadge('THIS_WEEK'), { label: 'This Week', tone: 'primary' })
  assert.deepEqual(getTechnicianTaskGroupBadge('LATER'), { label: 'Later', tone: 'neutral' })
  assert.deepEqual(getTechnicianTaskGroupBadge(null), { label: 'Later', tone: 'neutral' })
})

test('technician workload and task access rules match role specifications', () => {
  // Technician workload view (Lab Manager & Admin only)
  assert.equal(canViewTechnicianWorkload(['LAB_MANAGER']), true)
  assert.equal(canViewTechnicianWorkload(['INSTITUTION_ADMIN']), true)
  assert.equal(canViewTechnicianWorkload(['LAB_TECHNICIAN']), false)
  assert.equal(canViewTechnicianWorkload(['RESEARCHER_STUDENT']), false)
  assert.equal(canViewTechnicianWorkload(['SYSTEM_ADMIN']), false)

  // My Tasks access (Technicians, Managers, Admins)
  assert.equal(canAccessMyTasks(['LAB_TECHNICIAN']), true)
  assert.equal(canAccessMyTasks(['LAB_MANAGER']), true)
  assert.equal(canAccessMyTasks(['INSTITUTION_ADMIN']), true)
  assert.equal(canAccessMyTasks(['RESEARCHER_STUDENT']), false)
  assert.equal(canAccessMyTasks(['SYSTEM_ADMIN']), false)
})
