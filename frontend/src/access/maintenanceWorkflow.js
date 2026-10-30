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

export function canCreateMaintenance(roles) {
  return hasValidRoles(roles) && roles.some((role) => MAINTENANCE_CREATE_ROLES.includes(role))
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

export function buildMaintenanceCreatePayload({ equipmentId, description, priority }) {
  return { equipmentId: Number(equipmentId), description: description.trim(), priority }
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
