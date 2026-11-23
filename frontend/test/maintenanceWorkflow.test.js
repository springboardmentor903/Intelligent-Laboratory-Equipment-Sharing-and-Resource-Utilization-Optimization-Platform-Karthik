import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildAssignmentPayload,
  buildCalibrationCompletePayload,
  buildCalibrationSchedulePayload,
  buildDowntimePayload,
  buildMaintenanceCreatePayload,
  buildMaintenanceDateRangeParams,
  buildResolutionPayload,
  canCompleteCalibration,
  canCreateMaintenance,
  canScheduleCalibration,
  getCalibrationStatusBadge,
  getMaintenanceActionPlan,
  validateCalibrationComplete,
  validateCalibrationSchedule,
  validateDowntimeHours,
  validateMaintenanceCreate,
  validateMaintenanceDateRange,
  validateResolutionNotes,
  validateTechnicianId,
} from '../src/access/maintenanceWorkflow.js'

const roles = {
  researcher: ['RESEARCHER_STUDENT'],
  technician: ['LAB_TECHNICIAN'],
  manager: ['LAB_MANAGER'],
  head: ['DEPARTMENT_HEAD'],
  institution: ['INSTITUTION_ADMIN'],
  system: ['SYSTEM_ADMIN'],
}

test('maintenance reporting follows backend role permissions', () => {
  assert.equal(canCreateMaintenance(roles.researcher), true)
  assert.equal(canCreateMaintenance(roles.technician), true)
  assert.equal(canCreateMaintenance(roles.manager), true)
  assert.equal(canCreateMaintenance(roles.head), false)
  assert.equal(canCreateMaintenance(roles.institution), true)
  assert.equal(canCreateMaintenance(roles.system), true)
})

test('action plans follow assignment and lifecycle constraints by role', () => {
  const reported = { status: 'REPORTED', assignedTo: null }
  assert.deepEqual(getMaintenanceActionPlan(roles.manager, reported).transitions, ['CANCELLED'])
  assert.equal(getMaintenanceActionPlan(roles.manager, reported).canAssign, true)
  assert.equal(getMaintenanceActionPlan(roles.technician, reported).canAssign, false)
  assert.deepEqual(getMaintenanceActionPlan(roles.technician, { status: 'ASSIGNED', assignedTo: 8 }).transitions, ['IN_PROGRESS', 'CANCELLED'])
  assert.deepEqual(getMaintenanceActionPlan(roles.manager, { status: 'IN_PROGRESS', startedAt: '2026-10-25T10:00:00' }).transitions, ['COMPLETED', 'CANCELLED'])
  assert.deepEqual(getMaintenanceActionPlan(roles.manager, { status: 'COMPLETED' }).transitions, [])
  assert.equal(getMaintenanceActionPlan(roles.head, { status: 'REPORTED' }).canChangeStatus, false)
})

test('report payload uses only backend-supported fields and validates inputs', () => {
  const input = { equipmentId: '12', description: '  Broken seal  ', priority: 'HIGH' }
  assert.equal(validateMaintenanceCreate(input), '')
  assert.deepEqual(buildMaintenanceCreatePayload(input), { equipmentId: 12, description: 'Broken seal', priority: 'HIGH' })
  assert.match(validateMaintenanceCreate({ ...input, equipmentId: '' }), /equipment/i)
  assert.match(validateMaintenanceCreate({ ...input, description: ' ' }), /description/i)
  assert.match(validateMaintenanceCreate({ ...input, priority: 'URGENT' }), /priority/i)
})

test('assignment and resolution request bodies match controller contracts', () => {
  assert.equal(validateTechnicianId('21'), '')
  assert.match(validateTechnicianId('0'), /technician/i)
  assert.deepEqual(buildAssignmentPayload('21'), { technicianId: 21 })
  assert.equal(validateResolutionNotes(' Replaced bearing '), '')
  assert.match(validateResolutionNotes(' '), /required/i)
  assert.match(validateResolutionNotes('x'.repeat(1001)), /1000/i)
  assert.deepEqual(buildResolutionPayload(' Replaced bearing '), { resolutionNotes: 'Replaced bearing' })
})

test('date-range endpoint gets exact LocalDateTime parameter names', () => {
  const range = { start: '2026-10-01T00:00', end: '2026-10-25T23:59' }
  assert.deepEqual(buildMaintenanceDateRangeParams(range.start, range.end), range)
  assert.equal(validateMaintenanceDateRange(range.start, range.end), '')
  assert.match(validateMaintenanceDateRange(range.start, range.start), /later/i)
})

test('buildMaintenanceCreatePayload supports scheduled work orders with optional type and technician', () => {
  const input = {
    equipmentId: '15',
    description: 'Quarterly sensor check',
    priority: 'MEDIUM',
    maintenanceType: 'PREVENTIVE',
    scheduledDate: '2026-12-01T09:00',
    assignedTo: '8',
  }
  const payload = buildMaintenanceCreatePayload(input)
  assert.equal(payload.equipmentId, 15)
  assert.equal(payload.maintenanceType, 'PREVENTIVE')
  assert.equal(payload.scheduledDate, '2026-12-01T09:00')
  assert.equal(payload.assignedTo, 8)
})

test('downtime hours validation and payload builder enforce bounds', () => {
  assert.equal(validateDowntimeHours('3.5'), '')
  assert.match(validateDowntimeHours('-1'), /non-negative/i)
  assert.match(validateDowntimeHours('abc'), /non-negative/i)
  assert.match(validateDowntimeHours('1000'), /720/i)
  assert.deepEqual(buildDowntimePayload('4.5'), { downtimeHours: 4.5 })
})

test('calibration permissions, schedule, and complete workflows validate correctly', () => {
  assert.equal(canScheduleCalibration(roles.manager), true)
  assert.equal(canScheduleCalibration(roles.technician), false)
  assert.equal(canCompleteCalibration(roles.technician), true)
  assert.equal(canCompleteCalibration(roles.researcher), false)

  const scheduleValid = { equipmentId: '7', scheduledDate: '2026-12-10', assignedTo: '5', notes: 'Standard check' }
  assert.equal(validateCalibrationSchedule(scheduleValid), '')
  assert.deepEqual(buildCalibrationSchedulePayload(scheduleValid), {
    equipmentId: 7,
    scheduledDate: '2026-12-10',
    nextDueDate: '2026-12-10',
    assignedTo: 5,
    notes: 'Standard check',
  })

  const completeValid = {
    calibrationDate: '2026-11-20',
    result: 'PASS',
    nextDueDate: '2027-05-20',
    notes: 'Laser calibrated',
    certificateNumber: 'CERT-001',
  }
  assert.equal(validateCalibrationComplete(completeValid), '')
  assert.deepEqual(buildCalibrationCompletePayload(completeValid), completeValid)
  assert.match(validateCalibrationComplete({ ...completeValid, nextDueDate: '2026-11-19' }), /after/i)
})

test('calibration status badge maps states accurately', () => {
  assert.deepEqual(getCalibrationStatusBadge({ status: 'SCHEDULED' }), { label: 'Scheduled', tone: 'info' })
  assert.deepEqual(getCalibrationStatusBadge({ status: 'COMPLETED', result: 'PASS', nextDueDate: '2099-01-01' }), { label: 'Valid / Passed', tone: 'success' })
  assert.deepEqual(getCalibrationStatusBadge({ status: 'COMPLETED', result: 'FAIL', nextDueDate: '2099-01-01' }), { label: 'Failed', tone: 'danger' })
  assert.deepEqual(getCalibrationStatusBadge({ status: 'COMPLETED', result: 'PASS', nextDueDate: '2020-01-01' }), { label: 'Overdue', tone: 'danger' })
})
