import { hasValidRoles } from './roleAccess.js'

export const MAINTENANCE_CREATE_ROLES = Object.freeze([
  'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER',
  'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
export const MAINTENANCE_STATUS_ROLES = Object.freeze([
  'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
export const MAINTENANCE_ASSIGN_ROLES = Object.freeze([
  'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
export const MAINTENANCE_DELETE_ROLES = MAINTENANCE_ASSIGN_ROLES
export const MAINTENANCE_RESOLUTION_ROLES = MAINTENANCE_STATUS_ROLES
export const MAINTENANCE_PRIORITIES = Object.freeze(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])
export const MAINTENANCE_TYPES = Object.freeze(['CORRECTIVE', 'PREVENTIVE', 'CALIBRATION', 'INSPECTION'])
export const CALIBRATION_RESULTS = Object.freeze(['PASS', 'FAIL', 'CONDITIONAL'])
export const CALIBRATION_SCHEDULE_ROLES = Object.freeze(['LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN'])
export const CALIBRATION_COMPLETE_ROLES = Object.freeze([
  'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])

export function canCreateMaintenance(roles) {
  return hasValidRoles(roles) && roles.some((role) => MAINTENANCE_CREATE_ROLES.includes(role))
}

export function canScheduleCalibration(roles) {
  return hasValidRoles(roles) && roles.some((role) => CALIBRATION_SCHEDULE_ROLES.includes(role))
}

export function canCompleteCalibration(roles) {
  return hasValidRoles(roles) && roles.some((role) => CALIBRATION_COMPLETE_ROLES.includes(role))
}

export function getMaintenanceActionPlan(roles, record) {
  const valid = hasValidRoles(roles)
  const status = String(record?.status || '').toUpperCase()
  const assigned = record?.assignedTo != null
  const started = Boolean(record?.startedAt)
  let transitions = []
  if (status === 'REPORTED') transitions = [...(assigned ? ['ASSIGNED'] : []), 'CANCELLED']
  if (status === 'ASSIGNED') transitions = ['IN_PROGRESS', 'CANCELLED']
  if (status === 'IN_PROGRESS') transitions = [...(started ? ['COMPLETED'] : []), 'CANCELLED']

  return {
    canAssign: valid && MAINTENANCE_ASSIGN_ROLES.some((role) => roles.includes(role))
      && ['REPORTED', 'ASSIGNED'].includes(status),
    canChangeStatus: valid && MAINTENANCE_STATUS_ROLES.some((role) => roles.includes(role)),
    transitions,
    canDelete: valid && MAINTENANCE_DELETE_ROLES.some((role) => roles.includes(role)),
    canUpdateResolution: valid && MAINTENANCE_RESOLUTION_ROLES.some((role) => roles.includes(role)),
  }
}

export function validateMaintenanceCreate({ equipmentId, description, priority }) {
  if (!Number.isInteger(Number(equipmentId)) || Number(equipmentId) <= 0) return 'Select equipment.'
  if (!String(description || '').trim()) return 'Description is required.'
  if (String(description).trim().length > 1000) return 'Description must be 1000 characters or fewer.'
  if (!MAINTENANCE_PRIORITIES.includes(priority)) return 'Select a supported priority.'
  return ''
}

export function buildMaintenanceCreatePayload({
  equipmentId,
  description,
  priority,
  maintenanceType,
  scheduledDate,
  assignedTo,
}) {
  const payload = {
    equipmentId: Number(equipmentId),
    description: String(description || '').trim(),
    priority,
  }
  if (maintenanceType && MAINTENANCE_TYPES.includes(maintenanceType)) {
    payload.maintenanceType = maintenanceType
  }
  if (scheduledDate) {
    payload.scheduledDate = scheduledDate
  }
  if (Number.isInteger(Number(assignedTo)) && Number(assignedTo) > 0) {
    payload.assignedTo = Number(assignedTo)
  }
  return payload
}

export function validateTechnicianId(value) {
  return Number.isInteger(Number(value)) && Number(value) > 0
    ? '' : 'Enter a valid technician ID.'
}

export function buildAssignmentPayload(technicianId) {
  return { technicianId: Number(technicianId) }
}

export function validateResolutionNotes(value) {
  if (!String(value || '').trim()) return 'Resolution notes are required.'
  if (String(value).trim().length > 1000) return 'Resolution notes must be 1000 characters or fewer.'
  return ''
}

export function buildResolutionPayload(resolutionNotes) {
  return { resolutionNotes: resolutionNotes.trim() }
}

export function validateDowntimeHours(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) return 'Downtime must be a non-negative number.'
  if (num > 720) return 'Downtime cannot exceed 720 hours.'
  return ''
}

export function buildDowntimePayload(downtimeHours) {
  return { downtimeHours: Number(downtimeHours) }
}

export function validateCalibrationSchedule({ equipmentId, scheduledDate, nextDueDate }) {
  if (!Number.isInteger(Number(equipmentId)) || Number(equipmentId) <= 0) return 'Select equipment.'
  const targetDate = scheduledDate || nextDueDate
  if (!targetDate) return 'Choose a scheduled or due date.'
  return ''
}

export function buildCalibrationSchedulePayload({
  equipmentId,
  scheduledDate,
  nextDueDate,
  assignedTo,
  notes,
}) {
  const payload = {
    equipmentId: Number(equipmentId),
    scheduledDate: scheduledDate || nextDueDate,
    nextDueDate: nextDueDate || scheduledDate,
  }
  if (Number.isInteger(Number(assignedTo)) && Number(assignedTo) > 0) {
    payload.assignedTo = Number(assignedTo)
  }
  if (String(notes || '').trim()) {
    payload.notes = String(notes).trim()
  }
  return payload
}

export function validateCalibrationComplete({ calibrationDate, result, nextDueDate }) {
  if (!calibrationDate) return 'Calibration date is required.'
  if (!CALIBRATION_RESULTS.includes(result)) return 'Select a valid calibration result.'
  if (!nextDueDate) return 'Next due date is required.'
  const calTime = new Date(calibrationDate).getTime()
  const dueTime = new Date(nextDueDate).getTime()
  if (dueTime <= calTime) return 'Next due date must be after calibration date.'
  return ''
}

export function buildCalibrationCompletePayload({
  calibrationDate,
  result,
  nextDueDate,
  notes,
  certificateNumber,
}) {
  const payload = {
    calibrationDate,
    result,
    nextDueDate,
  }
  if (String(notes || '').trim()) {
    payload.notes = String(notes).trim()
  }
  if (String(certificateNumber || '').trim()) {
    payload.certificateNumber = String(certificateNumber).trim()
  }
  return payload
}

export function getCalibrationStatusBadge(record) {
  const status = String(record?.status || '').toUpperCase()
  if (status === 'SCHEDULED') return { label: 'Scheduled', tone: 'info' }

  if (record?.nextDueDate) {
    const today = new Date().toISOString().split('T')[0]
    if (record.nextDueDate < today) return { label: 'Overdue', tone: 'danger' }

    const thirtyDaysAhead = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0]
    if (record.nextDueDate <= thirtyDaysAhead) return { label: 'Due soon', tone: 'warning' }
  }

  const result = String(record?.result || '').toUpperCase()
  if (result === 'PASS') return { label: 'Valid / Passed', tone: 'success' }
  if (result === 'FAIL') return { label: 'Failed', tone: 'danger' }
  if (result === 'CONDITIONAL') return { label: 'Conditional', tone: 'warning' }

  return { label: 'Recorded', tone: 'neutral' }
}

export function buildMaintenanceDateRangeParams(start, end) {
  return { start, end }
}

export function validateMaintenanceDateRange(start, end) {
  if (!start || !end) return 'Choose both start and end times.'
  const startTime = new Date(start).getTime()
  const endTime = new Date(end).getTime()
  if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) {
    return 'End time must be later than start time.'
  }
  return ''
}
