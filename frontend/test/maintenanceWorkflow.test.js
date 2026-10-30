import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildAssignmentPayload,
  buildMaintenanceCreatePayload,
  buildMaintenanceDateRangeParams,
  buildResolutionPayload,
  canCreateMaintenance,
  getMaintenanceActionPlan,
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
